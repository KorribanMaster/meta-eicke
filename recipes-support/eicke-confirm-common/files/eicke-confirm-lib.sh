# Shared helpers for the eicke A/B boot-confirmation services. Sourced, not run.
#
# One lifecycle, three backends — after an update boots on trial, "confirm" the
# running slot so the next reset is stable; if the trial never confirms, the
# bootloader falls back to the previous slot:
#
#   backend            service            confirm action
#   -----------------  -----------------  --------------------------------------
#   grubenv (x86 disk) eicke-bootconfirm  grub-editenv: ustate=0 bootcount=0
#   uboot.env (ARM)    eicke-bootconfirm  fw_setenv:    ustate=0 bootcount=0
#   UEFI BootOrder     eicke-ipxeconfirm  efibootmgr:   promote running slot
#
# The grubenv/uboot.env backends share the "act only while on trial" skeleton
# (eicke_confirm_on_trial); the UEFI BootOrder backend has its own mechanics and
# uses only the shared logger.

# eicke_log <tag> <message...> — consistent journald logging for all services.
eicke_log() {
    _tag="$1"; shift
    logger -t "$_tag" "$@"
}

# eicke_confirm_on_trial <tag> <get_ustate_fn> <confirm_fn>
#   Env-var backends (grubenv / uboot.env): clear the swupdate trial state only
#   while an update is on trial (ustate=1), so an accepted slot persists and the
#   bootloader's rollback is disarmed. <get_ustate_fn> echoes the current ustate;
#   <confirm_fn> writes ustate=0 bootcount=0. Both are shell function names.
eicke_confirm_on_trial() {
    _tag="$1"; _get="$2"; _confirm="$3"
    if [ "$($_get)" = "1" ]; then
        if $_confirm; then
            eicke_log "$_tag" "boot confirmed: ustate -> 0 (slot accepted)"
        else
            eicke_log "$_tag" "WARNING: failed to clear trial state"
        fi
    fi
}

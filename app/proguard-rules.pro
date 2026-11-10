# Island keeps no reflection on its own classes: services, receivers and activities are kept by the manifest rules,
# and the few reflective calls (hidden Bluetooth / audio getters, StatusBarManager) target Android framework classes,
# which R8 never renames. So no extra keep rules are needed.

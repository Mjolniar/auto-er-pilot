package data.scripts.betterautopilot;

import lunalib.lunaSettings.LunaSettings;
import lunalib.lunaSettings.LunaSettingsListener;

/** LunaLib link. Only touched when LunaLib is enabled, so the mod runs without it. */
public class BALuna implements LunaSettingsListener {
    static void register() {
        if (!LunaSettings.hasSettingsListenerOfClass(BALuna.class)) LunaSettings.addSettingsListener(new BALuna());
    }

    static Boolean bool(String id) { return LunaSettings.getBoolean(BASettings.MOD, id); }
    static Integer integer(String id) { return LunaSettings.getInt(BASettings.MOD, id); }
    static Double dbl(String id) { return LunaSettings.getDouble(BASettings.MOD, id); }

    /** Saving in LunaLib's menu applies the new values at once. */
    @Override
    public void settingsChanged(String modId) {
        if (BASettings.MOD.equals(modId)) BASettings.load();
    }
}

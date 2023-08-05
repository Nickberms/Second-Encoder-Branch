package om.sstvencoder.Modes;

import om.sstvencoder.ModeInterfaces.IModeInfo;
import om.sstvencoder.ModeInterfaces.ModeSize;

class ModeInfo implements IModeInfo {
    private final Class<?> mModeClass;

    ModeInfo(Class<?> modeClass) {
        mModeClass = modeClass;
    }

    public String getModeClassName() {
        return mModeClass.getName();
    }

    public ModeSize getModeSize() {
        return mModeClass.getAnnotation(ModeSize.class);
    }
}
import os

res = 'android/app/src/main/res'
os.makedirs(res + '/drawable', exist_ok=True)
os.makedirs(res + '/mipmap-anydpi-v26', exist_ok=True)

BG = '''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient android:startX="54" android:startY="0"
                android:endX="54" android:endY="108" android:type="linear">
                <item android:offset="0" android:color="#FF26223A"/>
                <item android:offset="1" android:color="#FF0E0E12"/>
            </gradient>
        </aapt:attr>
    </path>
</vector>
'''

FG = '''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path
        android:pathData="M70,37 C62,28 38,30 38,43 C38,54 70,52 70,65 C70,78 46,80 38,71"
        android:strokeWidth="9"
        android:strokeLineCap="round"
        android:strokeLineJoin="round">
        <aapt:attr name="android:strokeColor">
            <gradient android:startX="38" android:startY="30"
                android:endX="70" android:endY="80" android:type="linear">
                <item android:offset="0" android:color="#FFC9B8FF"/>
                <item android:offset="1" android:color="#FFFF9EC4"/>
            </gradient>
        </aapt:attr>
    </path>
</vector>
'''

AD = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/sp_bg"/>
    <foreground android:drawable="@drawable/sp_fg"/>
</adaptive-icon>
'''

open(res + '/drawable/sp_bg.xml', 'w').write(BG)
open(res + '/drawable/sp_fg.xml', 'w').write(FG)
open(res + '/mipmap-anydpi-v26/ic_launcher.xml', 'w').write(AD)
open(res + '/mipmap-anydpi-v26/ic_launcher_round.xml', 'w').write(AD)
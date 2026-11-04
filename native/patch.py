import os, re

dst = 'android/app/src/main/java/' + os.environ['APP_ID'].replace('.', '/')
os.makedirs(dst, exist_ok=True)
for f in os.listdir('native'):
    if f.endswith('.java'):
        t = open('native/' + f).read()
        t = re.sub(r'^package .*;', 'package ' + os.environ['APP_ID'] + ';', t, count=1, flags=re.M)
        open(dst + '/' + f, 'w').write(t)

mf = 'android/app/src/main/AndroidManifest.xml'
s = open(mf).read()
if 'READ_MEDIA_AUDIO' not in s:
    perms = '''
    <uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE"/>
    <uses-permission android:name="android.permission.READ_MEDIA_AUDIO"/>
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32"/>
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"/>
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
    <uses-permission android:name="android.permission.WAKE_LOCK"/>
'''
    s = s.replace('</manifest>', perms + '</manifest>')
    s = s.replace('</application>',
        '<service android:name=".PlayerService" android:exported="false" '
        'android:stopWithTask="false" android:foregroundServiceType="mediaPlayback"/>\n</application>')
    open(mf, 'w').write(s)

st = 'android/app/src/main/res/values/styles.xml'
if os.path.exists(st):
    t = open(st).read().replace('@drawable/splash', '#FF17232F')
    open(st, 'w').write(t)
# Add jaudiotagger for editing physical files
bg = 'android/app/build.gradle'
if os.path.exists(bg):
    bs = open(bg).read()
    if 'jaudiotagger' not in bs:
        bs = bs.replace('dependencies {', 'dependencies {\n    implementation "net.jthink:jaudiotagger:3.0.1"')
        open(bg, 'w').write(bs)
import os, shutil

dst = 'android/app/src/main/java/com/rieman/softplayer'
os.makedirs(dst, exist_ok=True)
for f in os.listdir('native'):
    if f.endswith('.java'):
        shutil.copy('native/' + f, dst + '/' + f)

mf = 'android/app/src/main/AndroidManifest.xml'
s = open(mf).read()
if 'READ_MEDIA_AUDIO' not in s:
    perms = '''
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
    t = open(st).read().replace('@drawable/splash', '#FF121212')
    open(st, 'w').write(t)
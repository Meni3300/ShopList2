# ShopList — רשימת קניות חכמה (Android)

Kotlin · Jetpack Compose · Material 3 · Room · DataStore · Navigation Compose · MVVM · Offline · RTL עברית.

## בנייה (בלי להתקין כלום) — GitHub Actions
1. צור ריפו חדש ב-GitHub והעלה אליו את כל הקבצים (כולל התיקייה `.github`).
2. לשונית **Actions** → **Build APK** → **Run workflow**. (הבנייה רצה גם אוטומטית בכל העלאה.)
3. כשהריצה מסתיימת בירוק, היכנס אליה, ובתחתית העמוד ב-**Artifacts** הורד את **ShopList-APK**.
4. בתוך ה-ZIP: `app-release.apk` (מומלץ להתקנה) ו-`app-debug.apk`.

## בנייה ב-Android Studio
File → Open → תיקיית הפרויקט → המתן ל-Gradle Sync → Build → Build APK(s).
או משורת פקודה (JDK 17 + Android SDK 34): `./gradlew testDebugUnitTest assembleRelease`
הקובץ: `app/build/outputs/apk/release/app-release.apk`

## התקנה בטלפון
העתק את ה-APK לטלפון ופתח אותו. אם Android שואל — אפשר "התקנה ממקורות לא ידועים" לאפליקציה שממנה פתחת את הקובץ.
(ה-APK חתום במפתח debug — מתאים לשימוש אישי, לא להעלאה ל-Play Store.)

## מבנה
- `data/local` — Room: entities, DAO, database
- `data` — `ShopRepository`, `BackupJson` (ייצוא/ייבוא)
- `parser` — `ShoppingInputParser`, `QuantityMerger`
- `sorting` — `ShoppingListSorter`, `BranchOrderMerger`, `ShoppingSuggestionEngine`, `PurchaseStats`
- `ui` — `AppViewModel` + מסכים: home / shopping / stores / products / settings / history
- `widgets` — Widget + `QuickAddActivity`

## בדיקות
`app/src/test/.../LogicTests.kt` — נרמול, כפילויות, parsing כמויות, מיזוג כמויות, מיון, מוצרים חדשים,
סדר עצמאי לכל סניף, שמירת מיקומים של מוצרים שלא ברשימה, הצעות, סטטיסטיקות קנייה, ייצוא/ייבוא JSON.

## בית משותף (סנכרון בין טלפונים) — Firebase
הסנכרון נדלק רק כשיש קובץ `app/google-services.json`. בלעדיו האפליקציה עובדת כרגיל, מקומית.
1. console.firebase.google.com → Add project (בלי Analytics).
2. Add app → Android → שם החבילה: `com.shoplist.app` → הורד `google-services.json`.
3. Build → Firestore Database → Create database (Production mode, אזור קרוב).
4. Firestore → Rules → הדבק ופרסם:
```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /households/{code}/{document=**} {
      allow read, write: if true;
    }
  }
}
```
5. העלה את `google-services.json` לתוך התיקייה `app` בריפו (הריפו חייב להיות Private).
6. אחרי הבנייה: הגדרות ← "צור בית חדש" בטלפון אחד, ו"הצטרף לבית עם קוד" בשני.

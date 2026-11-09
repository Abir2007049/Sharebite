// Builds docs/report/ShareBite_Project_Report.docx.
// Screenshots are read from docs/report/screenshots/<name>.png. A missing screenshot becomes a
// clearly marked grey box saying exactly what to capture, so the report can be finished later
// by dropping the PNG in and running:  node build_report.js
const fs = require('fs');
const path = require('path');
const {
  Document, Packer, Paragraph, TextRun, ImageRun, HeadingLevel, AlignmentType, Table, TableRow,
  TableCell, WidthType, BorderStyle, ShadingType, LevelFormat, PageBreak, Footer, PageNumber,
  TableOfContents, VerticalAlign,
} = require('docx');

const SHOTS = path.join(__dirname, 'screenshots');
const OUT = path.join(__dirname, 'ShareBite_Project_Report.docx');
const FONT = 'Calibri';
const GREEN = '0B6B4F';
const CONTENT_W = 9026; // A4 width minus 1-inch margins, in DXA
let figureNo = 0;
const missing = [];

// ---------- small helpers ---------------------------------------------------------------------

function pngSize(file) {
  const b = fs.readFileSync(file);
  return { w: b.readUInt32BE(16), h: b.readUInt32BE(20) };
}

const p = (text, opts = {}) => new Paragraph({
  spacing: { after: 140, line: 300 },
  ...opts,
  children: (Array.isArray(text) ? text : [text]).map((t) => (typeof t === 'string' ? new TextRun(t) : t)),
});
const b = (t) => new TextRun({ text: t, bold: true });
const h1 = (t) => new Paragraph({ heading: HeadingLevel.HEADING_1, children: [new TextRun(t)] });
const h2 = (t) => new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun(t)] });
const bullet = (text) => new Paragraph({
  numbering: { reference: 'bullets', level: 0 },
  spacing: { after: 80, line: 290 },
  children: (Array.isArray(text) ? text : [text]).map((t) => (typeof t === 'string' ? new TextRun(t) : t)),
});
const caption = (text) => new Paragraph({
  alignment: AlignmentType.CENTER,
  spacing: { before: 60, after: 240 },
  children: [new TextRun({ text, italics: true, size: 19, color: '475467' })],
});

/** One image (or a placeholder box) sized to fit maxW x maxH inches. */
function imageBlock(name, maxW, maxH, what) {
  const file = path.join(SHOTS, name + '.png');
  if (!fs.existsSync(file)) {
    missing.push(`${name}.png  -  ${what}`);
    return new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { before: 120, after: 60 },
      shading: { type: ShadingType.CLEAR, fill: 'F2F4F7' },
      border: ['top', 'bottom'].reduce((o, k) => ({ ...o, [k]: { style: BorderStyle.DASHED, size: 8, color: '98A2B3', space: 6 } }), {}),
      children: [
        new TextRun({ text: 'Screenshot to add: ', bold: true, color: 'B42318' }),
        new TextRun({ text: what, color: '344054' }),
        new TextRun({ text: `  (save as screenshots/${name}.png)`, color: '98A2B3', size: 18 }),
      ],
    });
  }
  const { w, h } = pngSize(file);
  const scale = Math.min((maxW * 96) / w, (maxH * 96) / h);
  return new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { before: 120, after: 0 },
    children: [new ImageRun({ type: 'png', data: fs.readFileSync(file), transformation: { width: Math.round(w * scale), height: Math.round(h * scale) } })],
  });
}

/** A numbered figure: one or two phone screenshots side by side, or one wide image. */
function figure(items, text, wide = false) {
  figureNo++;
  const label = `Figure ${figureNo}: ${text}`;
  if (wide || items.length === 1) {
    const [name, what] = items[0];
    return [imageBlock(name, wide ? 6.2 : 2.6, wide ? 6.5 : 5.2, what), caption(label)];
  }
  const none = { style: BorderStyle.NONE, size: 0, color: 'FFFFFF' };
  const borders = { top: none, bottom: none, left: none, right: none };
  const colW = CONTENT_W / items.length;
  return [
    new Table({
      width: { size: CONTENT_W, type: WidthType.DXA },
      columnWidths: items.map(() => colW),
      borders: { ...borders, insideHorizontal: none, insideVertical: none },
      rows: [new TableRow({
        children: items.map(([name, what]) => new TableCell({
          width: { size: colW, type: WidthType.DXA },
          borders,
          verticalAlign: VerticalAlign.TOP,
          children: [imageBlock(name, 2.6, 5.0, what)],
        })),
      })],
    }),
    caption(label),
  ];
}

// ---------- the report ------------------------------------------------------------------------

const cover = [
  new Paragraph({ spacing: { before: 2200 }, children: [] }),
  new Paragraph({ alignment: AlignmentType.CENTER, children: [new TextRun({ text: 'ShareBite', bold: true, size: 72, color: GREEN })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 600 }, children: [new TextRun({ text: 'A food rescue and surplus sharing app for Android', size: 30, color: '344054' })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 1400 }, children: [new TextRun({ text: 'Project Report', size: 28, bold: true })] }),
  ...[
    ['Course', '[Course name and code]'],
    ['Submitted by', '[Your name]'],
    ['Student ID', '[Your ID]'],
    ['Submitted to', '[Teacher name]'],
    ['Date', 'November 2026'],
  ].map(([k, v]) => new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 100 }, children: [new TextRun({ text: k + ': ', bold: true }), new TextRun(v)] })),
  new Paragraph({ children: [new PageBreak()] }),
];

const toc = [
  new Paragraph({ heading: HeadingLevel.HEADING_1, children: [new TextRun('Contents')] }),
  new TableOfContents('Contents', { hyperlink: true, headingStyleRange: '1-2' }),
  p([new TextRun({ text: 'If the contents list looks empty, right-click it in Word and choose "Update Field".', italics: true, color: '98A2B3', size: 18 })]),
  new Paragraph({ children: [new PageBreak()] }),
];

const overview = [
  h1('1. Overview'),
  h2('1.1 Why I made ShareBite'),
  p('Every evening a lot of good food gets thrown away. Bakeries have bread left at closing time, restaurants cook more biryani than they sell, and canteens end up with full trays after lunch. At the same time there are people nearby who would happily take that food, and charities that cook for others and are always short.'),
  p('ShareBite connects these two sides. A restaurant or bakery posts the food it has left over, with the quantity, a pickup time and the food safety details. People and charities nearby see it, reserve it and come to collect it. The food gets eaten instead of going in the bin.'),
  h2('1.2 Who uses it'),
  p('The app has three kinds of users, and each one sees a slightly different app:'),
  bullet([b('Food providers'), ' (restaurants, bakeries, canteens, shops and home cooks) post surplus food, accept or decline requests and hand the food over.']),
  bullet([b('Receivers'), ' (individual people) find food near them and reserve up to 5 portions at a time, so one person cannot take everything.']),
  bullet([b('Charities'), ' can reserve bigger amounts and also see "charity only" donations. They have to be verified first, and only a reviewer can approve them, not the charity itself.']),
  p('You can also open the app as a guest and look around without making an account. You only need an account when you want to actually reserve something.'),
  h2('1.3 Main features at a glance'),
  bullet('Sign up and log in with email, with three account types.'),
  bullet('Home screen with nearby food, restaurants sharing food right now, and quick category shortcuts.'),
  bullet('Search, filters, sorting and a map of all available food.'),
  bullet('Food details with allergens, storage advice and live weather tips for the pickup.'),
  bullet('Restaurant pages with logo, cover photo and everything they offer.'),
  bullet('Reservations that can never oversell (they run as database transactions).'),
  bullet('Private chat between the provider and the person collecting, with online and typing status.'),
  bullet('QR code check at pickup, so the handover is verified.'),
  bullet('"I\'m on my way" live notification and pickup reminders.'),
  bullet('Personal allergen warnings, impact dashboard with a meals estimate, and photo upload.'),
];

const walkthrough = [
  h1('2. Feature walkthrough'),
  p('In this section I go through the app screen by screen, the same way I show it in the demo.'),

  h2('2.1 Welcome, guest mode and sign up'),
  p('The first screen explains what ShareBite does in three lines. From here you can create an account, log in, or tap "Browse food without an account" to look around as a guest. Guests can see the home screen, search, the map, food details and restaurant pages. When a guest tries to reserve, the app takes them to the login screen.'),
  p('When you sign up you pick your role (provider, receiver or charity). Passwords are handled by Firebase Authentication, so the app never stores them itself.'),
  ...figure([['app_welcome', 'Welcome screen'], ['app_register', 'Create account screen with the three roles']], 'Welcome screen and sign up with role selection'),

  h2('2.2 Home screen'),
  p('The home screen has the ShareBite logo at the top left and a greeting that changes with the time of day. Under the search bar there is a green banner with how many listings are available and how many end within the hour. The "Places sharing food now" row shows each restaurant with its logo and how many items it has. Below that are round category shortcuts and rows of food cards: near you, recently added, free food, discounted and charity donations.'),
  p('Each food card shows the photo, the restaurant logo, the price tag (Free, Charity or the discounted price in taka), how much is left and how long until pickup closes. Cards lift a little when you press them, and the time pill turns red when it is ending soon.'),
  ...figure([['app_home', 'Home screen (receiver), top part'], ['app_home_2', 'Home screen scrolled down to the food carousels']], 'Home screen with places, categories and food cards'),

  h2('2.3 Explore, search and map'),
  p('Explore is where you search properly. You can type a word (for example "biryani"), pick a category and a type (free, discounted, charity) and sort by newest, ending soon or nearest. "Nearest" and the distances only appear when the phone has a real location fix. The app never guesses a distance.'),
  p('On a wider screen or in landscape, Explore shows the cards in two or three columns instead of one. The map screen shows every available listing as a pin on OpenStreetMap. Tapping a pin shows the food, and you can open it or get directions in your maps app.'),
  ...figure([['app_explore', 'Explore tab with search and filters'], ['app_map', 'Map screen with food pins around Dhaka']], 'Explore with filters, and the map of available food'),

  h2('2.4 Food details'),
  p('The details screen has everything you need before you go: the photo (with the photographer credited when needed), price, how much is left, the pickup window, the address and a directions button. The food safety part shows when it was prepared, how to store it and the allergens. A yellow box warns about allergens, and if the food contains something on your personal allergen list you get a red warning at the top.'),
  p('There is also a small weather card. It uses the Open-Meteo API to get the weather at the pickup point and gives a tip, for example to bring a cool bag when it is hot and the food is perishable. The share button sends the listing to any app, like WhatsApp or Messenger.'),
  ...figure([['app_listing', 'Listing details screen'], ['app_listing_2', 'Same screen scrolled to food safety and weather']], 'Food details with allergens, weather advice and the provider'),

  h2('2.5 Restaurant pages'),
  p('Every provider has their own page with a cover photo, round logo, a short "about" text and all the food they are sharing right now. You get there from "See all from this place" on any listing or from the restaurant row on the home screen. Providers can change their logo, cover and text from Edit profile.'),
  ...figure([['app_storefront', 'A restaurant page (e.g. Golden Crust Bakery)'], ['app_edit_storefront', 'Edit profile for a provider with logo, cover and about text']], 'A restaurant page, and how a provider edits it'),

  h2('2.6 Reserving and tracking a reservation'),
  p('To reserve, you choose the quantity and confirm. The app shows the limit for your role and asks you to accept the allergen notice. Behind the scenes the reservation is saved as a Firestore transaction. It reads the stock again and takes your portions in one step, so two people can never get the last portions at the same time.'),
  p('The reservation then shows up in the Reservations tab. The details screen has a timeline (requested, confirmed, collected) and the pickup information. The provider gets a notification and can confirm or decline.'),
  ...figure([['app_reservations', 'Reservations tab'], ['app_reservation_detail', 'Reservation details with timeline and hand-over card']], 'Reservations list and a reservation with its timeline'),

  h2('2.7 Chat, "I\'m on my way" and QR pickup'),
  p('Once a request exists, the provider and the person collecting can chat inside the reservation, for example "I\'m at the side gate". The top of the chat shows if the other person is online or when they were last seen, and shows "typing..." while they type. Only these two people can read the chat.'),
  p('When the receiver leaves for the pickup they can tap "I\'m on my way". This starts a foreground service with a notification that keeps updating: the distance to the pickup point, the time left and a progress bar. It stops by itself when the food is collected or the time is over.'),
  p('At the pickup the receiver opens their QR code and the provider scans it. The app checks that the code matches a secret that only the receiver can read, and then the reservation becomes "Collected (verified by QR code)". This is checked by the database rules on the server, so nobody can fake it from a modified app.'),
  ...figure([['app_chat', 'Chat with online / typing status'], ['app_qr', 'Receiver showing the pickup QR code']], 'Chat between the two sides, and the pickup QR code'),
  ...figure([['app_onmyway', 'Notification shade showing the "On the way to ..." notification with distance and time left']], 'Live "On my way" notification from the foreground service'),

  h2('2.8 Provider side'),
  p('Providers see a dashboard instead of the normal home: their active listings, pending requests, completed pickups and recent activity. From "My listings" they can add, edit, close or withdraw food.'),
  p('The add listing form asks for the title, description, category, quantity, type (free, discounted or charity), the pickup time and address, and the food safety details. Allergens, storage and preparation time are required. The provider can choose a photo from the gallery or take one with the camera. The photo is compressed on a background thread before it is uploaded.'),
  ...figure([['app_provider_dashboard', 'Provider dashboard'], ['app_add_listing', 'Add listing form with the photo options']], 'Provider dashboard and the add listing form'),

  h2('2.9 Impact, profile and allergens'),
  p('The Impact tab shows what you have actually done. It only counts food that was really collected. It shows units shared or rescued, completed pickups, people or providers helped, a chart for the last six months and a list by category. There is also an estimate of meals. The ⓘ button explains how it is worked out: one portion is one meal, and kilograms are divided by 420 g per meal (the factor WRAP uses for surplus food). I did not add any CO₂ numbers because I did not have a proper model for them.'),
  p('In Profile you can edit your details, see notifications, read the food safety guidelines and set "My allergens". The allergen list is saved only on the phone, because it is health information.'),
  ...figure([['app_impact', 'Impact tab with meals estimate and chart'], ['app_profile', 'Profile with My allergens']], 'Impact dashboard and the profile screen'),

  h2('2.10 Reacting to the phone\'s state'),
  p('The app listens to system broadcasts. When airplane mode is turned on, a banner says you are offline and that your changes will sync later (Firestore keeps working from its cache). When Battery Saver is on or the battery is low, the app shows a banner and turns off the decorative animations to save power.'),
  ...figure([['app_airplane', 'Home screen with the airplane mode banner']], 'Banner shown after the airplane mode broadcast'),
];

const apiDb = [
  h1('3. APIs and database'),
  h2('3.1 External APIs'),
  p('ShareBite uses two free public APIs. Both are called with HttpURLConnection on a background thread, with timeouts and error handling. The JSON is parsed with Gson into small Java classes.'),
  bullet([b('Nominatim (OpenStreetMap)'), ' turns an address into coordinates and back. Providers use it when they set the pickup location of a listing.']),
  bullet([b('Open-Meteo'), ' gives the current temperature, humidity and weather code at the pickup point. The app turns this into food handling advice on the listing screen.']),
  p('The screenshots below are real responses. I made the same requests the app makes, with the same parameters, on 9 November 2026.'),
  ...figure([['api_nominatim', 'Nominatim response']], 'Nominatim geocoding response for "Banani, Dhaka" (JSON)', true),
  ...figure([['api_openmeteo', 'Open-Meteo response']], 'Open-Meteo current weather response for the pickup point (JSON)', true),

  h2('3.2 Firebase Authentication'),
  p('Users sign up and log in with email and password through Firebase Authentication. Sessions are kept, so you stay logged in. There is also a password reset by email. The app saves the user\'s profile (name, role, phone) in Firestore under the same user ID, but never the password.'),
  ...figure([['fb_auth_users', 'Firebase console > Authentication > Users (list of accounts)']], 'Firebase Authentication users', true),
  ...figure([['fb_auth_method', 'Firebase console > Authentication > Sign-in method (Email/Password enabled)']], 'Email/Password sign-in enabled', true),

  h2('3.3 Cloud Firestore'),
  p('Firestore is the main database. It has these collections:'),
  bullet([b('users'), ': profile, role, verification status, logo and cover for providers']),
  bullet([b('foodListings'), ': every food post with quantity, prices, pickup time, location, allergens and status']),
  bullet([b('reservations'), ': each request with its status timeline, plus a private sub-document for the QR code and a messages sub-collection for the chat']),
  bullet([b('notifications'), ': in-app notifications for each change']),
  bullet([b('charityVerifications'), ': details a charity sends for review']),
  bullet([b('images'), ' and ', b('privateImages'), ': the uploaded photos (see 3.5)']),
  p('All the important changes, like reserving, confirming, cancelling and collecting, run as transactions. The security rules check everything again on the server: who can change what, the role limits, the stock maths and the order of statuses. I tested the rules with 47 automated tests on the Firebase emulator.'),
  ...figure([['fb_firestore_data', 'Firebase console > Firestore Database > Data (foodListings with a document open)']], 'Firestore data', true),
  ...figure([['fb_firestore_rules', 'Firebase console > Firestore Database > Rules']], 'Firestore security rules', true),

  h2('3.4 Realtime Database'),
  p('I use Firebase Realtime Database for the live parts of the chat, because it has a feature made for this called onDisconnect. When someone opens the app, it writes them as "online" under status/<user id>. It also tells the server to write "offline" by itself if the phone loses connection or the app is closed. The chat reads this to show "Online" or "Last seen 5 minutes ago". The typing indicator works the same way under typing/<reservation id>/<user id>. The rules only let a user write their own status.'),
  ...figure([['fb_rtdb', 'Firebase console > Realtime Database > Data (status and typing nodes)']], 'Realtime Database with presence data', true),

  h2('3.5 Picture storage'),
  p('Photos are a big part of the app: food photos, restaurant logos and cover photos. Firebase Storage now needs a paid plan for new projects, so I store the pictures in Firestore instead. Before upload the app resizes the photo to at most 1024 pixels and compresses it to under 700 KB on a background thread. Then it saves the photo as a binary field in the images collection. A custom Glide loader reads it back and shows it with normal caching. The rules only allow JPEGs up to 750 KB, written by their owner, and only providers can upload food photos. Charity verification documents go to a separate private collection that only the charity and reviewers can read.'),
  p('The app can still use Firebase Storage. It only needs one build setting if the project ever gets a Storage bucket.'),
  ...figure([['fb_images', 'Firebase console > Firestore > images collection with one photo document open']], 'Photos stored in the images collection', true),
];

const closing = [
  h1('4. Testing'),
  p('I tested ShareBite on a real phone (Redmi Note 10 Lite) with the real Firebase project and demo data, and with automated tests:'),
  bullet('70 JVM unit tests for the business rules: stock, reservation status changes, role limits, validation, allergen matching, the meals estimate, QR codes, reminder timing and JSON parsing.'),
  bullet('47 security rules tests on the Firestore emulator. They try both normal actions and attacks, like overselling, faking a QR check, reading someone else\'s chat or a charity verifying itself.'),
  bullet('Android lint with no errors.'),

  h1('5. Limitations and future work'),
  bullet('Notifications from other users only appear while the app is open, because there is no push notification server (that needs paid Cloud Functions).'),
  bullet('Charity verification is approved by a reviewer in the Firebase console. There is no reviewer screen inside the app yet.'),
  bullet('Discounted food is paid in person at pickup. There is no online payment.'),
  bullet('The demo data is made up (fictional restaurants and people in Dhaka). The food photos come from Wikimedia Commons under open licences and are credited in the app.'),

  h1('6. Conclusion'),
  p('ShareBite does what I wanted it to do. A restaurant can post its leftover food in a minute, and someone nearby can find it, reserve it, talk to the restaurant and collect it with a quick QR scan. I built it in Java with MVVM, Firebase (Authentication, Firestore and Realtime Database), two web APIs and the main Android components: broadcasts, a content provider, a foreground service and background work. The thing I am happiest with is that the important rules are checked on the server too. Even if someone changes the app, they cannot oversell food or fake a pickup.'),
];

// ---------- document --------------------------------------------------------------------------

const doc = new Document({
  creator: 'ShareBite project',
  title: 'ShareBite Project Report',
  styles: {
    default: { document: { run: { font: FONT, size: 22 } } },
    paragraphStyles: [
      { id: 'Heading1', name: 'Heading 1', basedOn: 'Normal', next: 'Normal', quickFormat: true,
        run: { font: FONT, size: 34, bold: true, color: GREEN },
        paragraph: { spacing: { before: 360, after: 160 }, keepNext: true, outlineLevel: 0 } },
      { id: 'Heading2', name: 'Heading 2', basedOn: 'Normal', next: 'Normal', quickFormat: true,
        run: { font: FONT, size: 26, bold: true, color: '101828' },
        paragraph: { spacing: { before: 260, after: 100 }, keepNext: true, outlineLevel: 1 } },
    ],
  },
  numbering: {
    config: [{ reference: 'bullets', levels: [{ level: 0, format: LevelFormat.BULLET, text: '•', alignment: AlignmentType.LEFT,
      style: { paragraph: { indent: { left: 540, hanging: 270 } } } }] }],
  },
  features: { updateFields: true },
  sections: [{
    properties: { page: { margin: { top: 1440, bottom: 1440, left: 1440, right: 1440 } } },
    footers: {
      default: new Footer({ children: [new Paragraph({ alignment: AlignmentType.CENTER, children: [
        new TextRun({ text: 'ShareBite Project Report  |  Page ', size: 18, color: '98A2B3' }),
        new TextRun({ children: [PageNumber.CURRENT], size: 18, color: '98A2B3' }),
      ] })] }),
    },
    children: [...cover, ...toc, ...overview, ...walkthrough, ...apiDb, ...closing],
  }],
});

Packer.toBuffer(doc).then((buf) => {
  fs.writeFileSync(OUT, buf);
  console.log('Wrote', OUT, `(${figureNo} figures)`);
  if (missing.length) {
    console.log(`\n${missing.length} screenshots still to add (grey boxes in the report):`);
    missing.forEach((m) => console.log('  - ' + m));
  }
});

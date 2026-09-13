const fs = require("fs");
const path = require("path");

function findPath(...candidates) {
  for (const p of candidates) {
    if (fs.existsSync(p)) return p;
  }
  return candidates[0];
}

const configXML = findPath(
  path.resolve(__dirname, "../../../../config.xml"),
  path.resolve(__dirname, "../../../config.xml"),
  path.resolve(process.cwd(), "config.xml")
);
const menuJava = findPath(
  path.resolve(__dirname, "../../../../platforms/android/app/src/main/java/com/foxdebug/browser/Menu.java"),
  path.resolve(__dirname, "../../../platforms/android/app/src/main/java/com/foxdebug/browser/Menu.java"),
  path.resolve(process.cwd(), "platforms/android/app/src/main/java/com/foxdebug/browser/Menu.java")
);
const docProvider = findPath(
  path.resolve(__dirname, "../../../../platforms/android/app/src/main/java/com/foxdebug/acode/rk/exec/terminal/AlpineDocumentProvider.java"),
  path.resolve(__dirname, "../../../platforms/android/app/src/main/java/com/foxdebug/acode/rk/exec/terminal/AlpineDocumentProvider.java"),
  path.resolve(process.cwd(), "platforms/android/app/src/main/java/com/foxdebug/acode/rk/exec/terminal/AlpineDocumentProvider.java")
);

const repeatChar = (char, times) => char.repeat(times);

function replaceImport(filePath, appId) {
  if (!fs.existsSync(filePath)) {
    console.warn(`⚠ File not found: ${filePath}`);
    return;
  }

  const data = fs.readFileSync(filePath, "utf8");

  const updated = data.replace(
    /import\s+[0-9a-zA-Z._]+\.R;/,
    `import ${appId}.R;`
  );

  fs.writeFileSync(filePath, updated);
}

try {
  if (!fs.existsSync(configXML)) {
    throw new Error("config.xml not found");
  }

  const config = fs.readFileSync(configXML, "utf8");
  const match = /widget\s+id="([0-9a-zA-Z.\-_]+)"/.exec(config);

  if (!match) {
    throw new Error("Could not extract widget id from config.xml");
  }

  const appId = match[1];

  replaceImport(docProvider, appId);
  replaceImport(menuJava, appId);

  const msg = `==== Changed package to ${appId} ====`;

  console.log("\n" + repeatChar("=", msg.length));
  console.log(msg);
  console.log(repeatChar("=", msg.length) + "\n");

} catch (error) {
  console.error("❌ Error:", error.message);
  process.exit(1);
}
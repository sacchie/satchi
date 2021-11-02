const Application = require("spectron").Application;
const assert = require("assert");
const electronPath = require("electron");
const path = require("path");
const { spawn } = require("child_process");

class TestCases {
  constructor(app) {
    this.app = app;
  }

  showsAnInitialWindow() {
    return this.app.client.getWindowCount().then((count) => {
      assert.strictEqual(count, 1);
    });
  }

  async integrationWithMain_showingNotifications() {
    const messages = await this.app.client.$$(
      "*[data-testid=notification-card-content-message]"
    );
    assert.strictEqual(messages.length, 5);
    messages.forEach(async (m) => {
      const messageText = await m.getText();
      assert.strictEqual(messageText, "Hello");
    });
  }
}

describe("End-to-end tests", function () {
  let mainProcess;

  this.timeout(10000);

  before(function () {
    const options = {
      cwd: path.resolve(path.join("..", "main")),
      env: {
        ...process.env,
        GATEWAYS_PATH: path.resolve(path.join("test", "gateways.yml")),
      },
      stdio: "inherit",
    };
    mainProcess = spawn("java", ["-jar", "build/libs/main-1.0-SNAPSHOT-all.jar"], options);
    return new Promise((resolve) => {
      setTimeout(resolve, 3000); // TODO get rid of constant-time wait
    });
  });

  beforeEach(function () {
    this.app = new Application({
      path: electronPath,
      args: [path.join(__dirname, "..")],
    });
    this.testCases = new TestCases(this.app);
    return this.app.start();
  });

  afterEach(function () {
    if (this.app && this.app.isRunning()) {
      return this.app.stop();
    }
  });

  after(function () {
    if (!mainProcess.kill()) {
      throw new Error();
    }
  });

  it("shows an initial window", function () {
    return this.testCases.showsAnInitialWindow();
  });

  it("test", function () {
    return this.testCases.integrationWithMain_showingNotifications();
  });
});

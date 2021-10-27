const Application = require("spectron").Application;
const assert = require("assert");
const electronPath = require("electron");
const path = require("path");

class Spec {
  constructor(app) {
    this.app = app;
  }

  showsAnInitialWindow() {
    return this.app.client.getWindowCount().then((count) => {
      assert.equal(count, 1);
    });
  }
}

describe("Application launch", function () {
  this.timeout(10000);

  beforeEach(function () {
    this.app = new Application({
      path: electronPath,
      args: [path.join(__dirname, "..")],
    });
    this.spec = new Spec(this.app);
    return this.app.start();
  });

  afterEach(function () {
    if (this.app && this.app.isRunning()) {
      return this.app.stop();
    }
  });

  it("shows an initial window", function () {
    return this.spec.showsAnInitialWindow();
  });
});

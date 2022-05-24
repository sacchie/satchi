import * as PIXI from "pixi.js";
import { ShaderSystem, Renderer } from "@pixi/core";
import { install } from "@pixi/unsafe-eval";

install({ ShaderSystem });

// const renderer = new Renderer();

export function start(el) {
  const app = new PIXI.Application({ backgroundColor: 0xffffff });
  el.appendChild(app.view);

  const wallRect = new PIXI.Graphics();
  const WALL_LX = 0;
  const WALL_TY = 0;
  const WALL_RX = 300;
  const WALL_BY = 400;
  wallRect.lineStyle(2, 0x000000);
  wallRect.drawRect(WALL_LX, WALL_TY, WALL_RX, WALL_BY);
  app.stage.addChild(wallRect);

  const r = 5.0;
  const ball = new PIXI.Graphics();
  ball.beginFill(0xff0000);
  ball.drawCircle(0, 0, r);
  ball.position.x = 100.0;
  ball.position.y = 100.0;
  app.stage.addChild(ball);
  let v = { x: 7.0, y: 0.0 };

  animate();
  function animate() {
    requestAnimationFrame(animate);
    const dx = v.x;
    const dy = v.y;
    if (ball.x + dx > WALL_RX) {
      // hit right wall
      v.x = -0.9 * v.x;
    } else if (ball.x + dx < WALL_LX) {
      // hit left wall
      v.x = -0.9 * v.x;
    } else {
      // no hit
    }

    if (ball.y + dy > WALL_BY) {
      // hit bottom wall
      v.y = -0.9 * v.y;
    } else if (ball.y + dy < WALL_TY) {
      // hit top wall
      v.y = -0.9 * v.y;
    } else {
      // no hit
    }

    ball.x += v.x;
    ball.y += v.y;

    v.y += 0.1;

    app.render(app.stage);
  }
}

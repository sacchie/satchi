import * as PIXI from "pixi.js";
import { ShaderSystem, Renderer } from "@pixi/core";
import { install } from "@pixi/unsafe-eval";

install({ ShaderSystem });

// const renderer = new Renderer();

class Paddle {
  constructor(width, height, velocity) {
    this.width = width;
    this.height = height;
    this.velocity = velocity;
  }
}

class Ball {
  constructor(radius) {
    this.radius = radius;
  }
}

function moveBall(ballPosition, ballVelocity, WALL_LX, WALL_TY, WALL_RX, WALL_BY) {
  const dx = ballVelocity.x;
  const dy = ballVelocity.y;
  if (ballPosition.x + dx > WALL_RX) {
    // hit right wall
    ballVelocity.x *= -0.9;
  } else if (ballPosition.x + dx < WALL_LX) {
    // hit left wall
    ballVelocity.x *= -0.9;
  } else {
    // no hit
  }

  if (ballPosition.y + dy > WALL_BY) {
    // hit bottom wall
    ballVelocity.y *= -0.9;
  } else if (ballPosition.y + dy < WALL_TY) {
    // hit top wall
    ballVelocity.y *= -0.9;
  } else {
    // no hit
  }

  ballPosition.x += ballVelocity.x;
  ballPosition.y += ballVelocity.y;

  ballVelocity.y += 0.1;
}

export function start(el) {
  const state = {
    paddle: {
      pixi: null
    },
    ball: {
      pixi: null,
      velocity: {
        x: null,
        y: null
      }
    }
  }

  const app = new PIXI.Application({
    width: 300,
    height: 400,
    backgroundColor: 0xffffff
  });
  el.appendChild(app.view);

  const wallRect = new PIXI.Graphics();
  const WALL_LX = 0;
  const WALL_TY = 0;
  const WALL_RX = 300;
  const WALL_BY = 400;
  wallRect.lineStyle(2, 0x000000);
  wallRect.drawRect(WALL_LX, WALL_TY, WALL_RX, WALL_BY);
  app.stage.addChild(wallRect);

  const paddle = new Paddle(20, 5, 5);
  state.paddle.pixi = new PIXI.Graphics();
  state.paddle.pixi.lineStyle(2,  0x000000);
  state.paddle.pixi.drawRect(10, 350, paddle.width, paddle.height);
  app.stage.addChild(state.paddle.pixi);

  const ball = new Ball(5.0);
  state.ball.velocity.x = 7.0;
  state.ball.velocity.y = 0.0;
  state.ball.pixi = new PIXI.Graphics();
  state.ball.pixi.beginFill(0xff0000);
  state.ball.pixi.drawCircle(0, 0, ball.radius);
  state.ball.pixi.position.x = 100.0;
  state.ball.pixi.position.y = 100.0;
  app.stage.addChild(state.ball.pixi);

  animate();
  function animate() {
    requestAnimationFrame(animate);
    moveBall(state.ball.pixi.position, state.ball.velocity, WALL_LX, WALL_TY, WALL_RX, WALL_BY);
    app.render(app.stage);
  }

  document.addEventListener('keydown', onKeyDown);

  function onKeyDown(key) {
    if (key.keyCode === 37) {
      // Left arrow is 37
      state.paddle.pixi.position.x -= paddle.velocity
    } else if (key.keyCode === 39) {
      // Right arrow is 39
      state.paddle.pixi.position.x += paddle.velocity;
    }
  }
}

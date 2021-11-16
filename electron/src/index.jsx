import React from "react";
import ReactDOM from "react-dom";
import Card from "@material-ui/core/Card";
import CardContent from "@material-ui/core/CardContent";
import CloseIcon from "@material-ui/icons/Close";
import CardActions from "@material-ui/core/CardActions";
import IconButton from "@material-ui/core/IconButton";
import OpenInBrowserIcon from "@material-ui/icons/OpenInBrowser";
import Tooltip from "@material-ui/core/Tooltip";
import { CardHeader } from "@material-ui/core";
import Avatar from "@material-ui/core/Avatar";
import Typography from "@material-ui/core/Typography";
import AppBar from "@material-ui/core/AppBar";
import Toolbar from "@material-ui/core/Toolbar";
import Checkbox from "@material-ui/core/Checkbox";

function NotificationCard(props) {
  const avatar = props.source.iconUrl ? (
    <Tooltip title={props.source.name}>
      <Avatar src={props.source.iconUrl} alt={props.source.name} />
    </Tooltip>
  ) : (
    <Tooltip title={props.source.name}>
      <Avatar alt={props.source.name} />
    </Tooltip>
  );
  return (
    <Card style={props.mentioned ? { backgroundColor: "LightYellow" } : {}}>
      <CardHeader
        avatar={avatar}
        title={props.title}
        subheader={props.timestamp}
      />
      <CardContent>
        <Typography variant="body2">{props.message}</Typography>
      </CardContent>
      <CardActions>
        <Tooltip title="既読にする">
          <IconButton onClick={props.onMark}>
            <CloseIcon />
          </IconButton>
        </Tooltip>
        <Tooltip title="通知を開く">
          <IconButton onClick={props.onOpen}>
            <OpenInBrowserIcon />
          </IconButton>
        </Tooltip>
      </CardActions>
    </Card>
  );
}

class ReconnectableWebSocket {
  constructor(onError, onOpen, onMessage) {
    this.isReconnecting = false;

    this.onError = onError;
    this.onOpen = onOpen;
    this.onMessage = onMessage;

    this.ws;
  }

  reconnect() {
    if (this.isReconnecting) {
      return;
    }
    this.isReconnecting = true;

    this.ws = new WebSocket("ws://localhost:8037/connect");

    this.ws.addEventListener("open", () => {
      this.isReconnecting = false;
      this.onOpen();
    });

    this.ws.onclose = (e) => {
      this.isReconnecting = false;
      this.onError();
      this.ws = undefined;
    };

    this.ws.onmessage = (msg) => {
      this.onMessage(msg);
    };
  }

  send(data) {
    if (!this.ws) {
      throw new Error("no ws instance");
    }
    this.ws.send(data);
  }
}

let timer;

const reconnectableWebSocket = new ReconnectableWebSocket(
  () => {
    let timeToReconnect = 5 + 1;
    timer && clearInterval(timer);
    timer = setInterval(() => {
      timeToReconnect--;
      ReactDOM.render(
        <WebsocketError
          onReconnect={
            timeToReconnect > 0
              ? () => {
                  console.log("reconnect by button", timer);
                  reconnectableWebSocket.reconnect();
                  timer && clearInterval(timer);
                  timer = undefined;
                  console.log("timer cleared by button");
                }
              : undefined
          }
          timeToReconnect={timeToReconnect}
        />,
        document.getElementById("app")
      );
      if (timeToReconnect === 0) {
        console.log("reconnect by timer", timer);
        timer && clearInterval(timer);
        reconnectableWebSocket.reconnect();
        timer = undefined;
        console.log("timer cleared by timer");
      }
    }, 1000);
  },
  () => {
    client.notifications();
  },
  (msg) => {
    const outMessage = JSON.parse(msg.data);
    switch (outMessage.type) {
      case "UpdateView":
        ReactDOM.render(
          <App client={client} viewModel={outMessage.value} />,
          document.getElementById("app")
        );
        break;
      case "ShowDesktopNotification":
        new Notification(outMessage.value.title, {
          body: outMessage.value.body,
        }).onclick = (event) => {
          window.myAPI.openExternal(outMessage.value.url);
        };
        console.log("ShowDesktopNotification", outMessage);
        break;
    }
  }
);

class Client {
  constructor(ws) {
    this.ws = ws;
  }

  notifications() {
    this.ws.send(
      JSON.stringify({
        op: "Notifications",
      })
    );
  }

  markAsRead(notificationId, gatewayId) {
    this.ws.send(
      JSON.stringify({
        op: "MarkAsRead",
        args: { notificationId, gatewayId },
      })
    );
  }

  toggleMentioned() {
    this.ws.send(
      JSON.stringify({
        op: "ToggleMentioned",
      })
    );
  }
}

const client = new Client(reconnectableWebSocket);

function App({ client, viewModel }) {
  if (viewModel.stateClass === "LoadingState") {
    return <div>Loading...</div>;
  }

  if (viewModel.stateClass === "ViewingState") {
    return (
      <>
        <AppBar position="sticky">
          <Toolbar>
            <Typography component="h1" variant="h6" color="inherit">
              satchi
            </Typography>

            <Checkbox
              checked={viewModel.stateData.isMentionOnly}
              onChange={() => client.toggleMentioned()}
              name="mention"
              color="default"
              inputProps={{ "aria-label": "secondary checkbox" }}
            />
          </Toolbar>
        </AppBar>
        <div>
          {viewModel.stateData.notifications.map((n, index) => (
            <div key={index} style={{ padding: 5 }}>
              <NotificationCard
                {...n}
                onOpen={() => window.myAPI.openExternal(n.source.url)}
                onMark={() => client.markAsRead(n.id, n.gatewayId)}
              />
            </div>
          ))}
        </div>
      </>
    );
  }
  return null;
}

function WebsocketError(props) {
  return (
    <div>
      Websocket Error! Reconnect in {props.timeToReconnect} second(s)...
      <button onClick={props.onReconnect} disabled={!props.onReconnect}>
        Reconnect now
      </button>
    </div>
  );
}

reconnectableWebSocket.reconnect();

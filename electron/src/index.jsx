import React, { useState } from "react";
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

function listenWebsocket(
  ws,
  onOpen,
  onUpdateViewModel,
  onShowDesktopNotification,
  onError
) {
  ws.addEventListener("open", onOpen);

  ws.onmessage = (msg) => {
    const outMessage = JSON.parse(msg.data);
    switch (outMessage.type) {
      case "UpdateView":
        onUpdateViewModel(outMessage.value);
        break;
      case "ShowDesktopNotification":
        onShowDesktopNotification(outMessage.value);
        console.log("ShowDesktopNotification", outMessage);
        break;
    }
  };

  // 通常oncloseにはならないはずなのでerrorのcbをつめておく
  ws.onclose = ws.onerror = onError;
}

function Disconnected(props) {
  return (
    <div>
      Websocket Error!
      <button onClick={props.onReconnect}>Reconnect</button>
    </div>
  );
}

function connect() {
  function doConnect() {
    const ws = new WebSocket("ws://localhost:8037/connect");
    const client = new Client(ws);
    listenWebsocket(
      ws,
      // open
      () => client.notifications(),
      // update view
      (viewModel) => {
        ReactDOM.render(
          <App client={client} viewModel={viewModel} />,
          document.getElementById("app")
        );
      },
      // desktop notification
      (ntf) => {
        new Notification(ntf.title, {
          body: ntf.body,
        }).onclick = (event) => {
          window.myAPI.openExternal(ntf.url);
        };
      },
      // error
      () => {
        ReactDOM.render(
          <Disconnected onReconnect={connect} />,
          document.getElementById("app")
        );
      }
    );
  }
  ReactDOM.render(<div>Reconecting...</div>, document.getElementById("app"));
  setTimeout(doConnect, 1000);
}

connect();

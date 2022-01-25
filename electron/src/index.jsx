import React, { useState, useMemo, useEffect } from "react";
import ReactDOM from "react-dom";
import Card from "@material-ui/core/Card";
import CardContent from "@material-ui/core/CardContent";
import CloseIcon from "@material-ui/icons/Close";
import CardActions from "@material-ui/core/CardActions";
import IconButton from "@material-ui/core/IconButton";
import OpenInBrowserIcon from "@material-ui/icons/OpenInBrowser";
import MailIcon from "@material-ui/icons/Mail";
import Tooltip from "@material-ui/core/Tooltip";
import { CardHeader } from "@material-ui/core";
import Avatar from "@material-ui/core/Avatar";
import Typography from "@material-ui/core/Typography";
import AppBar from "@material-ui/core/AppBar";
import Button from "@material-ui/core/Button";
import Toolbar from "@material-ui/core/Toolbar";
import Menu from "@material-ui/core/Menu";
import MenuItem from "@material-ui/core/MenuItem";
import Checkbox from "@material-ui/core/Checkbox";
import SearchIcon from "@material-ui/icons/Search";
import SaveAltIcon from "@material-ui/icons/SaveAlt";
import ArrowDropDownIcon from "@material-ui/icons/ArrowDropDown";
import Input from "@material-ui/core/Input";

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

function addListenersToWebSocket(ws, listeners) {
  ws.addEventListener("open", listeners.onOpen);
  ws.onmessage = (msg) => {
    const outMessage = JSON.parse(msg.data);
    switch (outMessage.type) {
      case "UpdateView":
        listeners.onUpdateView(outMessage.value);
        break;
      case "ShowDesktopNotification":
        listeners.onShowDesktopNotification(outMessage.value);
        break;
    }
  };
  ws.onclose = listeners.onClose;
}

function render(component) {
  ReactDOM.render(component, document.getElementById("app"));
}

function connect() {
  function doConnect() {
    const ws = new WebSocket("ws://localhost:8037/connect");
    const client = new Client(ws);
    addListenersToWebSocket(ws, {
      onOpen: () => {
        client.notifications();
      },
      onUpdateView: (viewModel) => {
        render(<App client={client} viewModel={viewModel} />);
      },
      onShowDesktopNotification: (ntf) => {
        new Notification(ntf.title, {
          body: ntf.body,
        }).onclick = () => {
          window.myAPI.openExternal(ntf.url);
        };
      },
      onClose: (e) => {
        let timeToReconnect = 5 + 1;
        const timer = setInterval(() => {
          timeToReconnect--;
          render(
            <ConnectionClosed
              onReconnect={() => {
                clearInterval(timer);
                connect();
              }}
              timeToReconnect={timeToReconnect}
            />
          );
          if (timeToReconnect === 0) {
            clearInterval(timer);
            connect();
          }
        }, 1000);
      },
    });
  }
  render(<Connecting />);
  setTimeout(doConnect); // avoiding stack overflow
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

  changeFilterKeyword(keyword) {
    this.ws.send(
      JSON.stringify({
        op: "ChangeFilterKeyword",
        args: { keyword },
      })
    );
  }

  saveFilterKeyword(keyword) {
    this.ws.send(
      JSON.stringify({
        op: "SaveFilterKeyword",
        args: { keyword },
      })
    );
  }

  viewIncomingNotifications() {
    this.ws.send(
      JSON.stringify({
        op: "ViewIncomingNotifications",
      })
    );
  }
}

function App({ client, viewModel }) {
  const [keyword, setKeyword] = useState("");
  const [keywordSelectMenuAnchorEl, setKeywordSelectMenuAnchorEl] =
    useState(null);

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
            <SearchBox
              value={keyword}
              onChange={(keyword) => {
                setKeyword(keyword);
                client.changeFilterKeyword(keyword);
              }}
            />
            <IconButton
              onClick={() => {
                client.saveFilterKeyword(keyword);
              }}
            >
              <SaveAltIcon />
            </IconButton>
            {viewModel.stateData.savedKeywords.length > 0 && (
              <>
                <IconButton
                  onClick={(event) =>
                    setKeywordSelectMenuAnchorEl(event.target)
                  }
                >
                  <ArrowDropDownIcon />
                </IconButton>
                <SavedKeywordMenu
                  anchorEl={keywordSelectMenuAnchorEl}
                  onClose={() => setKeywordSelectMenuAnchorEl(null)}
                  keywords={viewModel.stateData.savedKeywords}
                  onSelect={(k) => {
                    setKeyword(k);
                    client.changeFilterKeyword(k);
                    setKeywordSelectMenuAnchorEl(null);
                  }}
                />
              </>
            )}
            <IncomingNotificationsButton
              count={viewModel.stateData.incomingNotificationCount}
              onClick={() => client.viewIncomingNotifications()}
            />
          </Toolbar>
        </AppBar>
        <NotificationCardList
          notifications={viewModel.stateData.notifications}
          client={client}
        />
      </>
    );
  }
  return null;
}

function NotificationCardList({ notifications, client }) {
  // avoid re-rendering unless notifications are changed, since rendering is slow
  const cards = useMemo(
    () =>
      notifications.map((n, index) => (
        <div key={index} style={{ padding: 5 }}>
          <NotificationCard
            {...n}
            onOpen={() => window.myAPI.openExternal(n.source.url)}
            onMark={() => client.markAsRead(n.id, n.gatewayId)}
          />
        </div>
      )),
    [notifications, client]
  );
  return <>{cards}</>;
}

function SearchBox({ value, onChange }) {
  return (
    <>
      <SearchIcon />
      <Input
        // disableUnderline
        style={{ color: "white" }}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </>
  );
}

function SavedKeywordMenu({ anchorEl, onClose, keywords, onSelect }) {
  return (
    <Menu anchorEl={anchorEl} open={Boolean(anchorEl)} onClose={onClose}>
      {keywords.map((k) => {
        return (
          <MenuItem
            key={k}
            onClick={() => {
              onSelect(k);
            }}
          >
            <Checkbox
              // checked={checked}
              onClick={(e) => {
                e.stopPropagation();
              }}
              // onChange={(e) => {  }}
            />
            <Typography>{k}</Typography>
          </MenuItem>
        );
      })}
    </Menu>
  );
}

function IncomingNotificationsButton({ count, onClick }) {
  if (count <= 0) {
    return null;
  }
  return (
    <Button
      variant="contained"
      color="primary"
      startIcon={<MailIcon />}
      onClick={onClick}
    >
      {`Load ${count} incoming notifications`}
    </Button>
  );
}

function Connecting(props) {
  return <div>Connecting...</div>;
}

function ConnectionClosed(props) {
  return (
    <div>
      Connection closed! Reconnect in {props.timeToReconnect} second(s)...
      <button onClick={props.onReconnect}>Reconnect now</button>
    </div>
  );
}

connect();

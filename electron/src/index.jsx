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
import * as game from "./game";

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
          <IconButton ref={props.markButtonRef} onClick={props.onMark}>
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

function cleanUpReactComponent() {
  ReactDOM.unmountComponentAtNode(document.getElementById("app"));
}

function connect(onStartGame) {
  function doConnect() {
    const ws = new WebSocket("ws://localhost:8037/connect");
    const client = new Client(ws);
    let closeByGameStart = false;

    function cleanUpReactAndStartGame() {
      closeByGameStart = true;
      ws.close();
    }

    addListenersToWebSocket(ws, {
      onOpen: () => {
        client.initializeView();
      },
      onUpdateView: (viewModel) => {
        render(
          <App
            client={client}
            viewModel={viewModel}
            onStartGame={cleanUpReactAndStartGame}
          />
        );
      },
      onShowDesktopNotification: (ntf) => {
        new Notification(ntf.title, {
          body: ntf.body,
        }).onclick = () => {
          window.myAPI.openExternal(ntf.url);
        };
      },
      onClose: (e) => {
        if (closeByGameStart) {
          cleanUpReactComponent();
          onStartGame();
          return;
        }

        let timeToReconnect = 5 + 1;
        const timer = setInterval(() => {
          timeToReconnect--;
          render(
            <ConnectionClosed
              onReconnect={() => {
                clearInterval(timer);
                connect(onStartGame);
              }}
              timeToReconnect={timeToReconnect}
            />
          );
          if (timeToReconnect === 0) {
            clearInterval(timer);
            connect(onStartGame);
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

  initializeView() {
    this.ws.send(
      JSON.stringify({
        op: "InitializeView",
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

  changeKeywordSelectionForDesktopNotification(keyword, selected) {
    this.ws.send(
      JSON.stringify({
        op: "ChangeKeywordSelectionForDesktopNotification",
        args: { keyword, selected },
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

function App({ client, viewModel, onStartGame }) {
  const [keyword, setKeyword] = useState("");
  const [keywordSelectMenuAnchorEl, setKeywordSelectMenuAnchorEl] =
    useState(null);
  const [viewingStateData, setViewingStateData] = useState(null);

  // Detecting change of the view model due to WebSocket messages
  useEffect(() => {
    if (viewModel.stateClass === "ViewingState") {
      const updates = {};
      for (const [prop, value] of Object.entries(viewModel.stateData)) {
        // ignore null-valued properties
        if (value !== null) {
          updates[prop] = value;
        }
      }
      setViewingStateData({ ...viewingStateData, ...updates });
    }
  }, [viewModel.stateData]);

  if (viewModel.stateClass === "LoadingState") {
    return <div>Loading...</div>;
  }

  if (viewModel.stateClass === "ViewingState" && viewingStateData !== null) {
    return (
      <>
        <AppBar position="sticky">
          <Toolbar>
            <Typography component="h1" variant="h6" color="inherit">
              satchi
            </Typography>
            <Checkbox
              checked={viewingStateData.isMentionOnly}
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
            {viewingStateData.savedKeywords.length > 0 && (
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
                  keywords={viewingStateData.savedKeywords}
                  onSelect={(k) => {
                    setKeyword(k);
                    client.changeFilterKeyword(k);
                    setKeywordSelectMenuAnchorEl(null);
                  }}
                  onChangeSelectionForDesktopNotification={(k, selected) => {
                    client.changeKeywordSelectionForDesktopNotification(
                      k,
                      selected
                    );
                  }}
                />
              </>
            )}
            <IncomingNotificationsButton
              count={viewingStateData.incomingNotificationCount}
              onClick={() => client.viewIncomingNotifications()}
            />
          </Toolbar>
        </AppBar>
        <Button onClick={onStartGame}>Switch to game</Button>
        <NotificationCardList
          notifications={viewingStateData.notifications}
          onOpen={(n) => window.myAPI.openExternal(n.source.url)}
          onMark={(n) => {
            setViewingStateData({
              ...viewingStateData,
              notifications: viewingStateData.notifications.filter(
                (ntf) => ntf.id !== n.id || ntf.gatewayId !== n.gatewayId
              ),
            });
            client.markAsRead(n.id, n.gatewayId);
          }}
        />
      </>
    );
  }
  return null;
}

class NotificationCardList extends React.Component {
  constructor(props) {
    super(props);

    this.makeKey = (n) => `${n.id}:${n.gatewayId}`;

    // https://ja.reactjs.org/docs/refs-and-the-dom.html#callback-refs
    this.markButtonEls = new Map();
    this.setMarkButtonElement = (n, element) => {
      this.markButtonEls.set(this.makeKey(n), element);
    };
  }

  render() {
    const cards = this.props.notifications.map((n, index) => (
      <div key={this.makeKey(n)} style={{ padding: 5 }}>
        <NotificationCard
          {...n}
          markButtonRef={(element) => this.setMarkButtonElement(n, element)}
          onOpen={() => this.props.onOpen(n)}
          onMark={() => {
            this.markButtonEls.delete(this.makeKey(n));
            if (index + 1 < this.props.notifications.length) {
              const key = this.makeKey(this.props.notifications[index + 1]);
              if (this.markButtonEls.has(key)) {
                this.markButtonEls.get(key).focus();
              }
            }

            this.props.onMark(n);
          }}
        />
      </div>
    ));
    return <>{cards}</>;
  }
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

function SavedKeywordMenu({
  anchorEl,
  onClose,
  keywords,
  onSelect,
  onChangeSelectionForDesktopNotification,
}) {
  return (
    <Menu anchorEl={anchorEl} open={Boolean(anchorEl)} onClose={onClose}>
      {keywords.map((entry) => {
        const k = entry.keyword;
        return (
          <MenuItem
            key={k}
            onClick={() => {
              onSelect(k);
            }}
          >
            <Checkbox
              checked={entry.selectedForDesktopNotification}
              onClick={(e) => {
                e.stopPropagation();
                onChangeSelectionForDesktopNotification(k, e.target.checked);
              }}
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

//

function startGame() {
  game.start(document.getElementById("app"));
}

connect(startGame);

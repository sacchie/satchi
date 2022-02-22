import React, { useState, useMemo, useEffect } from "react";
import ReactDOM from "react-dom";
import {Avatar,Button,Header, CheckBox,Card, CardBody, Text, Menu, TextInput, CardFooter, CardHeader, Grommet} from "grommet"

function NotificationCard(props) {
  const avatar = props.source.iconUrl ? (
    <Avatar src={props.source.iconUrl} alt={props.source.name} />
  ) : (
    <Avatar alt={props.source.name} />
  );
  return (
    <Card style={props.mentioned ? { backgroundColor: "LightYellow" } : {}}>
      <CardHeader
        avatar={avatar}
        title={props.title}
        subheader={props.timestamp}
      />
      <CardBody>
        <Text>{props.message}</Text>
      </CardBody>
      <CardFooter>
          <Button onClick={props.onMark}>
            ✕
          </Button>
          <Button onClick={props.onOpen}>
            ⤴️
          </Button>
      </CardFooter>
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

function App({ client, viewModel }) {
  const [keyword, setKeyword] = useState("");
  const [keywordSelectMenuAnchorEl, setKeywordSelectMenuAnchorEl] =
    useState(null);

  if (viewModel.stateClass === "LoadingState") {
    return <div>Loading...</div>;
  }

  if (viewModel.stateClass === "ViewingState") {
    return (
      <Grommet>
        <Header position="sticky">
            <Text tag="h1">
              satchi
            </Text>
            <CheckBox
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
            <Button
              onClick={() => {
                client.saveFilterKeyword(keyword);
              }}
            >
💾
            </Button>
            {viewModel.stateData.savedKeywords.length > 0 && (
              <>
                <Button
                  onClick={(event) =>
                    setKeywordSelectMenuAnchorEl(event.target)
                  }
                >
                  ⬇️
                </Button>
                <SavedKeywordMenu
                  anchorEl={keywordSelectMenuAnchorEl}
                  onClose={() => setKeywordSelectMenuAnchorEl(null)}
                  keywords={viewModel.stateData.savedKeywords}
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
              count={viewModel.stateData.incomingNotificationCount}
              onClick={() => client.viewIncomingNotifications()}
            />
        </Header>
        <NotificationCardList
          notifications={viewModel.stateData.notifications}
          client={client}
        />
      </Grommet>
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
      🔎
      <TextInput
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
      {/* {keywords.map((entry) => {
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
      })} */}
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
      onClick={onClick}
    >
      {`✉️ Load ${count} incoming notifications`}
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

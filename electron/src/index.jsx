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

function Notification(props) {
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

function App({ viewModel }) {
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
          </Toolbar>
        </AppBar>
        <div>
          {viewModel.stateData.notifications.map((n, index) => (
            <div key={index} style={{ padding: 5 }}>
              <Notification
                {...n}
                onOpen={() => window.myAPI.openExternal(n.source.url)}
                onMark={() =>
                  ws.send(
                    JSON.stringify({
                      op: "MarkAsRead",
                      args: {
                        notificationId: n.id,
                        gatewayId: n.gatewayId,
                      },
                    })
                  )
                }
              />
            </div>
          ))}
        </div>
      </>
    );
  }
  return null;
}

const ws = new WebSocket("ws://localhost:8037/view");
ws.onmessage = (msg) => {
  const data = JSON.parse(msg.data);
  ReactDOM.render(<App viewModel={data} />, document.getElementById("app"));
};

ws.addEventListener("open", (event) => {
  ws.send(
    JSON.stringify({
      op: "Notifications",
    })
  );
});

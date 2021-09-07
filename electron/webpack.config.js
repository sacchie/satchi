const path = require("path");
const webpack = require("webpack");

module.exports = {
  mode: "development",

  devtool: "inline-source-map",

  entry: {
    index: "./src/index.jsx",
  },

  target: "electron-renderer",

  output: {
    path: path.join(__dirname, "app", "compiled"),
    filename: "bundle.js",
  },

  module: {
    rules: [
      {
        test: /\.jsx$/,
        use: {
          loader: "babel-loader",
        },
      },
    ],
  },

  resolve: {
    extensions: [".js", ".jsx"],
  },
};

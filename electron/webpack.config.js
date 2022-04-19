const path = require("path");
const webpack = require("webpack");

module.exports = {
  mode: "development",

  devtool: "inline-source-map",

  entry: {
    index: "./src/index.jsx",
  },

  // https://qiita.com/umamichi/items/8781e426e9cd4a88961b
  target: "web",

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

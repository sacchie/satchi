package main.keywordmatch

import main.Notification

fun matchKeyword(ntf: Notification, keyword: String) =
    ntf.message.contains(keyword) || ntf.title.contains(keyword) || ntf.source.name.contains(keyword)

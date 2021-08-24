package main

fun main() {
    val credential = main.github.Credential.load("./.credential")
    println(main.github.notifications(credential));
}

package com.chat.user

import java.security.Principal
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

data class ChatSession(val user: ChatUser, val csrfHeader: String, val csrfToken: String)

@RestController
class UserController(private val users: UserRepository) {
    @GetMapping("/api/session")
    fun session(principal: Principal, csrf: CsrfToken): ChatSession =
        ChatSession(users.requireUser(principal.name), csrf.headerName, csrf.token)

    @GetMapping("/api/users")
    fun users(principal: Principal): List<ChatUser> = users.others(principal.name)
}

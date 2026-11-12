package com.chat.config

import com.chat.user.ChatUser
import com.chat.user.UserRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfig {
    @Bean
    fun securityFilterChain(http: HttpSecurity, users: UserRepository): SecurityFilterChain {
        val github = DefaultOAuth2UserService()
        http
            .authorizeHttpRequests {
                it.requestMatchers("/login", "/styles.css", "/error").permitAll()
                    .anyRequest().authenticated()
            }
            // SockJS POST transports use the CSRF token in the STOMP CONNECT frame instead.
            .csrf { it.ignoringRequestMatchers("/chat-websocket/**") }
            .oauth2Login { login ->
                login.loginPage("/login")
                login.userInfoEndpoint { info ->
                    info.userService { request ->
                        val user = github.loadUser(request)
                        val username = user.getAttribute<String>("login")
                            ?: throw OAuth2AuthenticationException(
                                OAuth2Error("invalid_user_info"), "GitHub login missing"
                            )
                        users.save(ChatUser(user.name, username))
                        user
                    }
                }
                login.defaultSuccessUrl("/", true).permitAll()
            }
            .logout { it.logoutSuccessUrl("/login?logout").permitAll() }
        return http.build()
    }
}

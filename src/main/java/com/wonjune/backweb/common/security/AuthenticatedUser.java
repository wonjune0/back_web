package com.wonjune.backweb.common.security;

public record AuthenticatedUser(Long id, String email, String name) {
}

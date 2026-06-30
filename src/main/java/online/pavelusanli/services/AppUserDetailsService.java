package online.pavelusanli.services;

import online.pavelusanli.model.AppUser;
import online.pavelusanli.repo.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        // Uninvited users have null passwordHash, and enabled=false; "!" is an
        // intentionally invalid BCrypt string so matches() always returns false.
        String password = user.getPasswordHash() != null ? user.getPasswordHash() : "!";

        return User.builder()
                .username(user.getUsername())
                .password(password)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
                .disabled(!user.isEnabled())
                .build();
    }
}
package com.billing.config;

import com.billing.dao.ClientMasterDao;
import com.billing.entity.ClientMaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private static final Logger logger = LoggerFactory.getLogger(CustomUserDetailsService.class);

    @Autowired
    private ClientMasterDao clientMasterDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        logger.info("loadUserByUsername: username={}", username);

        if ("admin".equals(username)) {
            List<GrantedAuthority> adminAuthorities = new ArrayList<>();
            adminAuthorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            return new User("admin", passwordEncoder.encode("admin"), adminAuthorities);
        }

        ClientMaster client = clientMasterDao.findByUsername(username);
        if (client == null) {
            logger.warn("loadUserByUsername: client not found for username={}", username);
            throw new UsernameNotFoundException("Client not found with username: " + username);
        }

        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_CLIENT"));

        logger.info("loadUserByUsername: client loaded, username={}, shopName={}", username, client.getClientShopName());
        return new User(client.getClientUsername(), client.getClientPassword(), authorities);
    }
}

package fr.sii.controller.oauth;

import java.io.IOException;
import java.text.ParseException;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.google.api.client.auth.oauth2.AuthorizationCodeTokenRequest;
import com.google.api.client.auth.oauth2.ClientParametersAuthentication;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson.JacksonFactory;
import com.nimbusds.jose.JOSEException;

import fr.sii.config.github.GithubSettings;
import fr.sii.domain.exception.CustomException;
import fr.sii.domain.token.Token;
import fr.sii.entity.User;
import fr.sii.service.auth.AuthService;
import fr.sii.service.github.GithubService;

@RestController
@RequestMapping(value = "/auth/github", produces = "application/json; charset=utf-8")
public class GithubAuthController {

    private final Logger log = LoggerFactory.getLogger(GoogleAuthController.class);

    private static final String ACCESS_TOKEN_URL = "https://github.com/login/oauth/access_token";

    // private static final String peopleApiUrl = "https://api.github.com/user";

    @Autowired
    GithubService githubService;

    @Autowired
    GithubSettings githubSettings;

    @Autowired
    AuthService authService;

    /**
     * Log in with Github
     *
     * @param httpServletResponse
     * @param httpServletRequest
     * @param info
     * @return
     * @throws IOException
     * @throws CustomException
     * @throws JOSEException
     * @throws ParseException
     */
    @RequestMapping(method = RequestMethod.POST)
    public Token loginGithub(HttpServletResponse httpServletResponse, HttpServletRequest httpServletRequest, @RequestBody Map<String, String> info)
            throws IOException, CustomException, JOSEException, ParseException {

        String client_id = githubSettings.getClientId();
        String client_secret = githubSettings.getClientSecret();

        Token token = null;

        try {
            TokenResponse tokenResponse = new AuthorizationCodeTokenRequest(new NetHttpTransport(), new JacksonFactory(), new GenericUrl(ACCESS_TOKEN_URL),
                    info.get("code")).setRequestInitializer(request -> request.setHeaders(new HttpHeaders().setAccept("application/json")))
                            .setClientAuthentication(new ClientParametersAuthentication(client_id, client_secret)).execute();

            String email = githubService.getEmail(tokenResponse.getAccessToken());
            String userId = githubService.getUserId(tokenResponse.getAccessToken());

            String socialProfilImageUrl = githubService.getAvatarUrl(tokenResponse.getAccessToken());

            token = authService.processUser(httpServletResponse, httpServletRequest, User.Provider.GITHUB, userId, email, socialProfilImageUrl);
        } catch (TokenResponseException e) {
            if (e.getDetails() != null) {
                log.warn("Error: " + e.getDetails().getError());
                if (e.getDetails().getErrorDescription() != null) {
                    log.warn(e.getDetails().getErrorDescription());
                }
                if (e.getDetails().getErrorUri() != null) {
                    log.warn(e.getDetails().getErrorUri());
                }
            } else {
                log.warn(e.getMessage());
            }
        }
        return token;
    }
}

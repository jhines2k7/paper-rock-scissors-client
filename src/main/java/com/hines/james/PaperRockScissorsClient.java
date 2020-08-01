package com.hines.james;

import io.rsocket.SocketAcceptor;
import io.rsocket.metadata.WellKnownMimeType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler;
import org.springframework.security.rsocket.metadata.SimpleAuthenticationEncoder;
import org.springframework.security.rsocket.metadata.UsernamePasswordMetadata;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import javax.annotation.PreDestroy;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@ShellComponent
public class PaperRockScissorsClient {
    private static final String CLIENT_ID = UUID.randomUUID().toString();
    private static final MimeType SIMPLE_AUTH = MimeTypeUtils.parseMimeType(WellKnownMimeType.MESSAGE_RSOCKET_AUTHENTICATION.getString());
    private static Disposable disposable;

    private RSocketRequester rsocketRequester;
    private RSocketRequester.Builder rsocketRequesterBuilder;
    private RSocketStrategies rsocketStrategies;

    @Autowired
    public PaperRockScissorsClient(RSocketRequester.Builder builder, @Qualifier("rSocketStrategies") RSocketStrategies strategies) {
        this.rsocketRequesterBuilder = builder;
        this.rsocketStrategies = strategies;
    }

    @ShellMethod("Login with your username and password.")
    public void login(String username, String password) {
        log.info("Connecting using client ID: {} and username: {}", CLIENT_ID, username);

        SocketAcceptor responder = RSocketMessageHandler.responder(rsocketStrategies, new ClientHandler());
        UsernamePasswordMetadata user = new UsernamePasswordMetadata(username, password);

        this.rsocketRequester = rsocketRequesterBuilder
                .setupRoute("shell-client")
                .setupData(CLIENT_ID)
                .setupMetadata(user, SIMPLE_AUTH)
                .rsocketStrategies(builder ->
                        builder.encoder(new SimpleAuthenticationEncoder()))
                .rsocketConnector(connector -> connector.acceptor(responder))
                .connectTcp("localhost", 7000)
                .block();

        this.rsocketRequester.rsocket()
                .onClose()
                .doOnError(error -> log.warn("Connection CLOSED"))
                .doFinally(consumer -> log.info("Client DISCONNECTED"))
                .subscribe();
    }

    @PreDestroy
    @ShellMethod("Logout and close your connection")
    public void logout() {
        if (userIsLoggedIn()) {
            this.s();
            this.rsocketRequester.rsocket().dispose();
            log.info("Logged out.");
        }
    }

    private boolean userIsLoggedIn() {
        if (null == this.rsocketRequester || this.rsocketRequester.rsocket().isDisposed()) {
            log.info("No connection. Did you login?");
            return false;
        }
        return true;
    }

    @ShellMethod("Stops Streams or Channels.")
    public void s() {
        if (userIsLoggedIn() && null != disposable) {
            log.info("Stopping the current stream.");

            disposable.dispose();

            log.info("Stream stopped.");
        }
    }

    @ShellMethod("Send a request to increase the number of challengers in the challenge stream")
    public void addChallenger(String username, String wins, String losses, String draws) throws InterruptedException {
        if (userIsLoggedIn()) {
            log.info("\nSending one request. Waiting for one response...");

            Challenger challenger = new Challenger(
                    username,
                    Integer.parseInt(wins),
                    Integer.parseInt(losses),
                    Integer.parseInt(draws),
                    PlayerStatus.WAITING
            );

            Challenger response = this.rsocketRequester
                    .route("new-challenger")
                    .data(challenger)
                    .retrieveMono(Challenger.class)
                    .block();

            log.info("\nResponse was: {}", response);
        }
    }

    @ShellMethod("Send one request. Many responses (stream) will be printed.")
    public void challengerStream(String playerId, String clientId) throws InterruptedException {
        if (userIsLoggedIn()) {
            log.info("\n\n**** Request-Stream\n**** Send one request.\n**** Log responses.\n**** Type 's' to stop.");

            disposable = this.rsocketRequester
                .route("challenger-stream")
                .data(new ChallengeRequestDTO(Integer.parseInt(playerId), clientId))
                .retrieveFlux(Challenger.class)
                .subscribe(challenger -> log.info("Response: {} \n(Type 's' to stop.)", challenger));
        }
    }
}

@Slf4j
class ClientHandler {

    @MessageMapping("client-status")
    public Flux<String> statusUpdate(String status) {
        log.info("Connection {}", status);
        return Flux.interval(Duration.ofSeconds(5)).map(index -> String.valueOf(Runtime.getRuntime().freeMemory()));
    }
}

package fr.xephi.authme.listener;

import fr.xephi.authme.cache.auth.PlayerCache;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.hooks.PluginHooks;
import fr.xephi.authme.runner.BeforeInjecting;
import fr.xephi.authme.runner.InjectDelayed;
import fr.xephi.authme.runner.DelayedInjectionRunner;
import fr.xephi.authme.settings.NewSetting;
import fr.xephi.authme.settings.properties.RegistrationSettings;
import fr.xephi.authme.settings.properties.RestrictionSettings;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.player.PlayerEvent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.util.Arrays;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

/**
 * Test for {@link ListenerService}.
 */
@RunWith(DelayedInjectionRunner.class)
public class ListenerServiceTest {

    @InjectDelayed
    private ListenerService listenerService;

    @Mock
    private NewSetting settings;

    @Mock
    private DataSource dataSource;

    @Mock
    private PluginHooks pluginHooks;

    @Mock
    private PlayerCache playerCache;

    @BeforeInjecting
    public void initializeDefaultSettings() {
        given(settings.getProperty(RegistrationSettings.FORCE)).willReturn(true);
        given(settings.getProperty(RestrictionSettings.UNRESTRICTED_NAMES)).willReturn(
            Arrays.asList("npc1", "npc2", "npc3"));
    }

    @Test
    public void shouldHandleEventWithNullEntity() {
        // given
        EntityEvent event = mock(EntityEvent.class);
        given(event.getEntity()).willReturn(null);

        // when
        boolean result = listenerService.shouldCancelEvent(event);

        // then
        assertThat(result, equalTo(false));
    }

    @Test
    public void shouldHandleEntityEventWithNonPlayerEntity() {
        // given
        EntityEvent event = mock(EntityEvent.class);
        given(event.getEntity()).willReturn(mock(Entity.class));

        // when
        boolean result = listenerService.shouldCancelEvent(event);

        // then
        assertThat(result, equalTo(false));
    }

    @Test
    public void shouldAllowAuthenticatedPlayer() {
        // given
        String playerName = "Bobby";
        Player player = mockPlayerWithName(playerName);
        given(playerCache.isAuthenticated(playerName)).willReturn(true);
        EntityEvent event = mock(EntityEvent.class);
        given(event.getEntity()).willReturn(player);

        // when
        boolean result = listenerService.shouldCancelEvent(event);

        // then
        assertThat(result, equalTo(false));
        verify(playerCache).isAuthenticated(playerName);
        verifyZeroInteractions(dataSource);
    }

    @Test
    public void shouldDenyUnLoggedPlayer() {
        // given
        String playerName = "Tester";
        Player player = mockPlayerWithName(playerName);
        given(playerCache.isAuthenticated(playerName)).willReturn(false);
        EntityEvent event = mock(EntityEvent.class);
        given(event.getEntity()).willReturn(player);

        // when
        boolean result = listenerService.shouldCancelEvent(event);

        // then
        assertThat(result, equalTo(true));
        verify(playerCache).isAuthenticated(playerName);
        // makes sure the setting is checked first = avoid unnecessary DB operation
        verifyZeroInteractions(dataSource);
    }

    @Test
    public void shouldAllowUnloggedPlayerForOptionalRegistration() {
        // given
        String playerName = "myPlayer1";
        Player player = mockPlayerWithName(playerName);
        given(playerCache.isAuthenticated(playerName)).willReturn(false);
        given(settings.getProperty(RegistrationSettings.FORCE)).willReturn(false);
        EntityEvent event = mock(EntityEvent.class);
        given(event.getEntity()).willReturn(player);
        listenerService.loadSettings(settings);

        // when
        boolean result = listenerService.shouldCancelEvent(event);

        // then
        assertThat(result, equalTo(false));
        verify(playerCache).isAuthenticated(playerName);
        verify(dataSource).isAuthAvailable(playerName);
    }

    @Test
    public void shouldAllowUnrestrictedName() {
        // given
        String playerName = "Npc2";
        Player player = mockPlayerWithName(playerName);
        EntityEvent event = mock(EntityEvent.class);
        given(event.getEntity()).willReturn(player);

        // when
        boolean result = listenerService.shouldCancelEvent(event);

        // then
        assertThat(result, equalTo(false));
        verifyZeroInteractions(dataSource);
    }

    @Test
    public void shouldAllowNpcPlayer() {
        // given
        String playerName = "other_npc";
        Player player = mockPlayerWithName(playerName);
        EntityEvent event = mock(EntityEvent.class);
        given(event.getEntity()).willReturn(player);
        given(pluginHooks.isNpc(player)).willReturn(true);

        // when
        boolean result = listenerService.shouldCancelEvent(event);

        // then
        assertThat(result, equalTo(false));
        verify(pluginHooks).isNpc(player);
    }

    @Test
    // This simply forwards to shouldCancelEvent(Player), so the rest is already tested
    public void shouldHandlePlayerEvent() {
        // given
        String playerName = "example";
        Player player = mockPlayerWithName(playerName);
        PlayerEvent event = new TestPlayerEvent(player);
        given(playerCache.isAuthenticated(playerName)).willReturn(true);

        // when
        boolean result = listenerService.shouldCancelEvent(event);

        // then
        assertThat(result, equalTo(false));
        verify(playerCache).isAuthenticated(playerName);
        verifyZeroInteractions(dataSource);
    }

    @Test
    public void shouldHandlePlayerEventWithNullPlayer() {
        // given
        PlayerEvent event = new TestPlayerEvent(null);

        // when
        boolean result = listenerService.shouldCancelEvent(event);

        // then
        assertThat(result, equalTo(false));
    }

    @Test
    // The previous tests verify most of shouldCancelEvent(Player)
    public void shouldVerifyBasedOnPlayer() {
        // given
        String playerName = "player";
        Player player = mockPlayerWithName(playerName);

        // when
        boolean result = listenerService.shouldCancelEvent(player);

        // then
        assertThat(result, equalTo(true));
        verify(playerCache).isAuthenticated(playerName);
        verifyZeroInteractions(dataSource);
        verify(pluginHooks).isNpc(player);
    }

    private static Player mockPlayerWithName(String name) {
        Player player = mock(Player.class);
        given(player.getName()).willReturn(name);
        return player;
    }

    /**
     * Test implementation of {@link PlayerEvent} (necessary because
     * {@link PlayerEvent#getPlayer()} is declared final).
     */
    private static final class TestPlayerEvent extends PlayerEvent {
        public TestPlayerEvent(Player player) {
            super(player);
        }

        @Override
        public HandlerList getHandlers() {
            return null;
        }
    }
}

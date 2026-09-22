package org.spon.edolnotify.telegram;

import io.github.natanimn.telebof.BotContext;
import io.github.natanimn.telebof.types.updates.CallbackQuery;
import org.junit.jupiter.api.Test;
import org.spon.edolnotify.telegram.callbacks.Callback;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallbackHandlerTest {

    @Test
    void routesMetadataAndPushAllByWireActionInsteadOfBeanName() {
        Callback metadata = mock(Callback.class);
        Callback pushAll = mock(Callback.class);
        when(metadata.action()).thenReturn("metadata");
        when(pushAll.action()).thenReturn("pushall");
        CallbackHandler handler = new CallbackHandler(List.of(metadata, pushAll));
        BotContext context = mock(BotContext.class);

        CallbackQuery metadataQuery = query("metadata");
        CallbackQuery pushAllQuery = query("pushall");

        handler.handleCallback(context, metadataQuery);
        handler.handleCallback(context, pushAllQuery);

        verify(metadata).handleCallback(context, metadataQuery);
        verify(pushAll).handleCallback(context, pushAllQuery);
    }

    @Test
    void rejectsDuplicateWireActionsAtStartup() {
        Callback first = mock(Callback.class);
        Callback second = mock(Callback.class);
        when(first.action()).thenReturn("metadata");
        when(second.action()).thenReturn("metadata");
        List<Callback> callbacks = List.of(first, second);

        assertThatThrownBy(() -> new CallbackHandler(callbacks))
                .isInstanceOf(IllegalStateException.class);
    }

    private CallbackQuery query(String action) {
        CallbackQuery query = mock(CallbackQuery.class, RETURNS_DEEP_STUBS);
        when(query.getData()).thenReturn(action + "_" + UUID.randomUUID());
        when(query.getMessage().getChat().getId()).thenReturn(1L);
        return query;
    }
}

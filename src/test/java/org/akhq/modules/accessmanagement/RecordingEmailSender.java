package org.akhq.modules.accessmanagement;

import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.email.Email;
import io.micronaut.email.EmailSender;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Test double: keeps every email instead of talking to SMTP. Enabled by {@code akhq.test.record-email=true}. */
@Primary
@Singleton
@Requires(property = "akhq.test.record-email", value = "true")
public class RecordingEmailSender implements EmailSender<Object, Object> {

    public final List<Email> sent = new CopyOnWriteArrayList<>();

    @Override
    @NonNull
    public Object send(@NonNull Email.Builder emailBuilder, @NonNull Consumer<Object> emailRequest) {
        Email email = emailBuilder.build();
        sent.add(email);
        return email;
    }

    @Override
    @NonNull
    public String getName() {
        return "recording";
    }
}

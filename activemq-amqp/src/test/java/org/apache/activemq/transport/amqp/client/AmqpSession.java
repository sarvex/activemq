/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.transport.amqp.client;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.activemq.transport.amqp.client.util.ClientFuture;
import org.apache.activemq.transport.amqp.client.util.UnmodifiableSession;
import org.apache.qpid.proton.engine.Connection;
import org.apache.qpid.proton.engine.Session;

/**
 * Session class that manages a Proton session endpoint.
 */
public class AmqpSession extends AmqpAbstractResource<Session> {

    private final AtomicLong receiverIdGenerator = new AtomicLong();
    private final AtomicLong senderIdGenerator = new AtomicLong();

    private final AmqpConnection connection;
    private final String sessionId;

    /**
     * Create a new session instance.
     *
     * @param connection
     * 		  The parent connection that created the session.
     * @param sessionId
     *        The unique ID value assigned to this session.
     */
    public AmqpSession(AmqpConnection connection, String sessionId) {
        this.connection = connection;
        this.sessionId = sessionId;
    }

    /**
     * Create a sender instance using the given address
     *
     * @param address
     * 	      the address to which the sender will produce its messages.
     *
     * @return a newly created sender that is ready for use.
     *
     * @throws Exception if an error occurs while creating the sender.
     */
    public AmqpSender createSender(final String address) throws Exception {
        checkClosed();

        final AmqpSender sender = new AmqpSender(AmqpSession.this, address, getNextSenderId());
        final ClientFuture request = new ClientFuture();

        connection.getScheduler().execute(new Runnable() {

            @Override
            public void run() {
                checkClosed();
                sender.open(request);
                pumpToProtonTransport();
            }
        });

        request.sync();

        return sender;
    }

    /**
     * Create a receiver instance using the given address
     *
     * @param address
     * 	      the address to which the receiver will subscribe for its messages.
     *
     * @return a newly created receiver that is ready for use.
     *
     * @throws Exception if an error occurs while creating the receiver.
     */
    public AmqpReceiver createReceiver(String address) throws Exception {
        checkClosed();

        final AmqpReceiver receiver = new AmqpReceiver(AmqpSession.this, address, getNextReceiverId());
        final ClientFuture request = new ClientFuture();

        connection.getScheduler().execute(new Runnable() {

            @Override
            public void run() {
                checkClosed();
                receiver.open(request);
                pumpToProtonTransport();
            }
        });

        request.sync();

        return receiver;
    }

    /**
     * @return this session's parent AmqpConnection.
     */
    public AmqpConnection getConnection() {
        return connection;
    }

    public Session getSession() {
        return new UnmodifiableSession(getEndpoint());
    }

    //----- Internal getters used from the child AmqpResource classes --------//

    ScheduledExecutorService getScheduler() {
        return connection.getScheduler();
    }

    Connection getProtonConnection() {
        return connection.getProtonConnection();
    }

    void pumpToProtonTransport() {
        connection.pumpToProtonTransport();
    }

    //----- Private implementation details -----------------------------------//

    @Override
    protected void doOpenInspection() {
        getStateInspector().inspectOpenedResource(getSession());
    }

    @Override
    protected void doClosedInspection() {
        getStateInspector().inspectClosedResource(getSession());
    }

    private String getNextSenderId() {
        return sessionId + ":" + senderIdGenerator.incrementAndGet();
    }

    private String getNextReceiverId() {
        return sessionId + ":" + receiverIdGenerator.incrementAndGet();
    }

    private void checkClosed() {
        if (isClosed()) {
            throw new IllegalStateException("Session is already closed");
        }
    }

    @Override
    public String toString() {
        return "AmqpSession { " + sessionId + " }";
    }
}

/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.reactivex.netty.client.loadbalancer;

import io.reactivex.netty.channel.Connection;
import io.reactivex.netty.client.ConnectionProvider;
import io.reactivex.netty.client.ConnectionProviderFactory;
import io.reactivex.netty.client.HostConnector;
import io.reactivex.netty.client.loadbalancer.HostCollector.HostUpdate;
import io.reactivex.netty.client.loadbalancer.HostCollector.HostUpdate.Action;
import io.reactivex.netty.internal.VoidToAnythingCast;
import java.util.logging.Level;
import java.util.logging.Logger;
import rx.Observable;
import rx.Single;
import rx.functions.Func1;

import java.util.List;

public class LoadBalancerFactory<W, R> implements ConnectionProviderFactory<W, R> {

    private static final Logger logger = Logger.getLogger(LoadBalancerFactory.class.getName());

    private final LoadBalancingStrategy<W, R> strategy;
    private final HostCollector collector;

    private LoadBalancerFactory(LoadBalancingStrategy<W, R> strategy, HostCollector collector) {
        this.strategy = strategy;
        this.collector = collector;
    }

    @Override
    public ConnectionProvider<W, R> newProvider(Observable<HostConnector<W, R>> hosts) {

        return new ConnectionProviderImpl(hosts.map(connector -> {
            HostHolder<W, R> newHolder = strategy.toHolder(connector);
            connector.subscribe(newHolder.getEventListener());
            return newHolder;
        }).flatMap((Func1<HostHolder<W, R>, Observable<HostUpdate<W, R>>>) holder -> holder.getConnector()
                     .getHost()
                     .getCloseNotifier()
                     .map(new VoidToAnythingCast<HostUpdate<W, R>>())
                     .ignoreElements()
                     .onErrorResumeNext(Observable.<HostUpdate<W, R>>empty())
                     .concatWith(Observable.just(new HostUpdate<>(Action.Remove, holder)))
                     .mergeWith(Observable.just(new HostUpdate<>(Action.Add, holder)))).flatMap(newCollector(collector.<W, R>newCollector()), 1).distinctUntilChanged());
    }

    public static <WW, RR> LoadBalancerFactory<WW, RR> create(LoadBalancingStrategy<WW, RR> strategy) {
        return create(strategy, new NoBufferHostCollector());
    }

    public static <WW, RR> LoadBalancerFactory<WW, RR> create(LoadBalancingStrategy<WW, RR> strategy,
                                                              HostCollector collector) {
        return new LoadBalancerFactory<>(strategy, collector);
    }

    private class ConnectionProviderImpl implements ConnectionProvider<W, R> {

        private volatile ConnectionProvider<W, R> currentProvider = () ->
                Observable.error(NoHostsAvailableException.EMPTY_INSTANCE);

        public ConnectionProviderImpl(Observable<List<HostHolder<W, R>>> hosts) {
            hosts.subscribe(hostHolders -> currentProvider = strategy.newStrategy(hostHolders),
                    throwable -> logger.log(Level.SEVERE, "Error while listening on the host stream. Hosts will not be refreshed.", throwable));
        }

        @Override
        public Observable<Connection<R, W>> newConnectionRequest() {
            return currentProvider.newConnectionRequest();
        }
    }

    private Func1<? super HostUpdate<W, R>, ? extends Observable<List<HostHolder<W, R>>>>
    newCollector(final Func1<HostUpdate<W, R>, Single<List<HostHolder<W, R>>>> f) {
        return (Func1<HostUpdate<W, R>, Observable<List<HostHolder<W, R>>>>) holder ->
                f.call(holder).toObservable();
    }
}

/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.kuujo.raft.state;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.kuujo.raft.ReplicationServiceEndpoint;
import net.kuujo.raft.StateMachine;
import net.kuujo.raft.log.Log;
import net.kuujo.raft.protocol.PingRequest;
import net.kuujo.raft.protocol.PollRequest;
import net.kuujo.raft.protocol.SubmitRequest;
import net.kuujo.raft.protocol.SyncRequest;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;

/**
 * A node state context.
 *
 * @author Jordan Halterman
 */
public class StateContext {
  private final Vertx vertx;
  private final ReplicationServiceEndpoint endpoint;
  private final Log log;
  private final StateMachine stateMachine;
  private final StateFactory stateFactory = new StateFactory();
  private StateType stateType;
  private State state;
  private List<Set<String>> configs = new ArrayList<>();
  private long electionTimeout = 2500;
  private long syncInterval = 1000;
  private String currentLeader;
  private long currentTerm;
  private String votedFor;
  private long commitIndex;
  private long lastApplied;

  public StateContext(Vertx vertx, ReplicationServiceEndpoint endpoint, Log log, StateMachine stateMachine) {
    this.vertx = vertx;
    this.endpoint = endpoint;
    this.log = log;
    this.stateMachine = stateMachine;
    transition(StateType.START);
  }

  public StateContext transition(StateType type) {
    if (type.equals(stateType)) return this;
    System.out.println(address() + " transitioning to " + type.getName());
    State oldState = state;
    stateType = type;
    switch (type) {
      case START:
        state = stateFactory.createStart()
            .setVertx(vertx)
            .setEndpoint(endpoint)
            .setStateMachine(stateMachine)
            .setLog(log)
            .setContext(this);
        break;
      case FOLLOWER:
        state = stateFactory.createFollower()
            .setVertx(vertx)
            .setEndpoint(endpoint)
            .setStateMachine(stateMachine)
            .setLog(log)
            .setContext(this);
        break;
      case CANDIDATE:
        state = stateFactory.createCandidate()
            .setVertx(vertx)
            .setEndpoint(endpoint)
            .setStateMachine(stateMachine)
            .setLog(log)
            .setContext(this);
        break;
      case LEADER:
        state = stateFactory.createLeader()
            .setVertx(vertx)
            .setEndpoint(endpoint)
            .setStateMachine(stateMachine)
            .setLog(log)
            .setContext(this);
        break;
    }

    if (oldState != null) {
      oldState.shutDown(new Handler<Void>() {
        @Override
        public void handle(Void _) {
          state.startUp(new Handler<Void>() {
            @Override
            public void handle(Void _) {
              state.configure(members());
            }
          });
        }
      });
    }
    else {
      state.startUp(new Handler<Void>() {
        @Override
        public void handle(Void _) {
          state.configure(members());
        }
      });
    }
    registerHandlers(state);
    return this;
  }

  private void registerHandlers(final State state) {
    endpoint.pingHandler(new Handler<PingRequest>() {
      @Override
      public void handle(PingRequest request) {
        state.handlePing(request);
      }
    });
    endpoint.syncHandler(new Handler<SyncRequest>() {
      @Override
      public void handle(SyncRequest request) {
        state.handleSync(request);
      }
    });
    endpoint.pollHandler(new Handler<PollRequest>() {
      @Override
      public void handle(PollRequest request) {
        state.handlePoll(request);
      }
    });
    endpoint.submitHandler(new Handler<SubmitRequest>() {
      @Override
      public void handle(SubmitRequest request) {
        state.handleSubmit(request);
      }
    });
  }

  /**
   * Returns the endpoint address.
   *
   * @return
   *   The current endpoint address.
   */
  public String address() {
    return endpoint.address();
  }

  /**
   * Returns the endpoint to which the state belongs.
   *
   * @return
   *   The parent endpoint.
   */
  public ReplicationServiceEndpoint endpoint() {
    return endpoint;
  }

  /**
   * Returns the state log.
   *
   * @return
   *   The state log.
   */
  public Log log() {
    return log;
  }

  /**
   * Returns the current leader.
   *
   * @return
   *   The current leader.
   */
  public String currentLeader() {
    return currentLeader;
  }

  /**
   * Sets the current leader.
   *
   * @param address
   *   The current leader.
   * @return
   *   The state context.
   */
  public StateContext setCurrentLeader(String address) {
    currentLeader = address;
    return this;
  }

  /**
   * Returns the current term.
   *
   * @return
   *   The current term.
   */
  public long currentTerm() {
    return currentTerm;
  }

  /**
   * Sets the current term.
   *
   * @param term
   *   The current term.
   * @return
   *   The state context.
   */
  public StateContext setCurrentTerm(long term) {
    currentTerm = term;
    return this;
  }

  /**
   * Returns the address of the member last voted for.
   *
   * @return
   *   The address of the member last voted for.
   */
  public String votedFor() {
    return votedFor;
  }

  /**
   * Sets the address of the member last voted for.
   *
   * @param address
   *   The address of the member last voted for.
   * @return
   *   The state context.
   */
  public StateContext setVotedFor(String address) {
    votedFor = address;
    return this;
  }

  /**
   * Returns the current commit index.
   *
   * @return
   *   The current commit index.
   */
  public long commitIndex() {
    return commitIndex;
  }

  /**
   * Sets the current commit index.
   *
   * @param index
   *   The current commit index.
   * @return
   *   The state context.
   */
  public StateContext setCommitIndex(long index) {
    commitIndex = index;
    return this;
  }

  /**
   * Returns the last index applied to the state machine.
   *
   * @return
   *   The last index applied to the state machine.
   */
  public long lastApplied() {
    return lastApplied;
  }

  /**
   * Sets the last index applied to the state machine.
   *
   * @param index
   *   The last index applied to the state machine.
   * @return
   *   The state context.
   */
  public StateContext setLastApplied(long index) {
    lastApplied = index;
    return this;
  }

  /**
   * Returns the election timeout.
   *
   * @return
   *   The election timeout.
   */
  public long electionTimeout() {
    return electionTimeout;
  }

  /**
   * Sets the election timeout.
   *
   * @param timeout
   *   The election timeout.
   * @return
   *   The state context.
   */
  public StateContext setElectionTimeout(long timeout) {
    electionTimeout = timeout;
    return this;
  }

  /**
   * Returns the synchronization interval.
   *
   * @return
   *   The synchronization interval.
   */
  public long syncInterval() {
    return syncInterval;
  }

  /**
   * Sets the synchronization interval.
   *
   * @param interval
   *   The interval at which the leader should synchronize with followers.
   * @return
   *   The state context.
   */
  public StateContext setSyncInterval(long interval) {
    syncInterval = interval;
    return this;
  }

  /**
   * Returns a boolean indicating whether the context contains a member.
   *
   * @param address
   *   The member address.
   * @return
   *   Indicates whether the context has a member.
   */
  public boolean hasMember(String address) {
    return members().contains(address);
  }

  /**
   * Adds a member to the cluster.
   *
   * @param address
   *   The new member address.
   * @return
   *   The state context.
   */
  public StateContext addMember(String address) {
    Set<String> members = members();
    if (!members.contains(address)) {
      members.add(address);
      appendConfig(members);
      state.configure(members);
    }
    return this;
  }

  /**
   * Removes a member from the cluster.
   *
   * @param address
   *   The member address to remove.
   * @return
   *   The state context.
   */
  public StateContext removeMember(String address) {
    Set<String> members = members();
    if (members.contains(address)) {
      members.remove(address);
      appendConfig(members);
      state.configure(members);
    }
    return this;
  }

  /**
   * Returns a set of current known cluster members.
   *
   * @return
   *   A set of current cluster members.
   */
  public Set<String> members() {
    Set<String> members = new HashSet<>();
    for (Set<String> config : configs) {
      members.addAll(config);
    }
    return members;
  }

  /**
   * Appends a configuration.
   *
   * @param members
   *   A set of members to append.
   * @return
   *   A set of all configured members.
   */
  public Set<String> appendConfig(Set<String> members) {
    configs.add(members);
    return members();
  }

  /**
   * Removes and returns the first configuration.
   *
   * @return
   *   The first configuration.
   */
  public Set<String> removeConfig() {
    if (configs.size() > 1) {
      return configs.remove(0);
    }
    return null;
  }

  /**
   * Returns a list of all configurations.
   *
   * @return
   *   A list of all configurations.
   */
  public List<Set<String>> configs() {
    return configs;
  }

}
/*
 * Copyright 2021-present Open Networking Foundation
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
 */
package org.nrl.airs.airman;

import org.apache.karaf.shell.commands.Command;
import org.onosproject.cli.AbstractShellCommand;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.commands.Option;


import org.onosproject.cli.AbstractChoicesCompleter;
import org.onosproject.cli.AbstractShellCommand;
import org.slf4j.helpers.MessageFormatter;

import com.google.common.collect.Sets;

/*import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
*/

import org.onlab.util.Tools;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.Device;
import org.onosproject.net.Host;
import org.onosproject.net.Link;
import org.onosproject.net.config.NetworkConfigService;
import org.onosproject.net.device.DeviceAdminService;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.group.GroupService;
import org.onosproject.net.host.HostAdminService;
import org.onosproject.net.intent.Intent;
import org.onosproject.net.intent.IntentEvent;
import org.onosproject.net.intent.IntentListener;
import org.onosproject.net.intent.IntentService;
import org.onosproject.net.intent.Key;
import org.onosproject.net.link.LinkAdminService;
import org.onosproject.net.meter.MeterService;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.packet.PacketRequest;
import org.onosproject.net.region.RegionAdminService;
import org.onosproject.ui.UiExtensionService;
import org.onosproject.ui.UiTopoLayoutService;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static org.onosproject.net.intent.IntentState.WITHDRAWN;

/**
 * Sample Apache Karaf CLI command
 */
@Command(scope = "onos", name = "airman",
         description = "airman command")
public class AppCommand extends AbstractShellCommand {
  public static final String CMD_CLEAR_HOST_TABLE = "clear_host_table";
  public static final String CMD_CLEAR_FLOW_TABLE = "clear_flow_table";
  public static final String CMD_CLEAR_SWITCH_TABLE = "clear_switch_table";


  public static final String CMD_CANCEL = "cancel";
  public static final String CMD_HELP = "help";


  // Arguments/Options
  @Argument(index = 0, name = "command", required = true,
    description = "name of the command")
  private String commandStr = null;

  @Argument(index = 1, name = "param...", required = false, multiValued = true,
    description = "param(s) required by commands")
  private List<String> paramList = new ArrayList<>();


    @Override
    protected void execute() {

	commandStr = Optional.ofNullable(commandStr).orElse("Error");

	switch (commandStr) {
      case CMD_CLEAR_HOST_TABLE: {
	wipeOutHosts();

        break;
      }
      case CMD_CLEAR_FLOW_TABLE: {
	wipeOutFlows();

        break;
      }
      case CMD_CLEAR_SWITCH_TABLE: {
	wipeOutDevices();

        break;
      }
      case CMD_HELP: {
        break;
      }
      default: {
        print("Unknown command %s", commandStr);
        break;
      }
    }

    }

    private void wipeOutIntents() {
        print("Wiping intents");
        IntentService intentService = get(IntentService.class);
        Set<Key> keysToWithdrawn = Sets.newConcurrentHashSet();
        Set<Intent> intentsToWithdrawn = Tools.stream(intentService.getIntents())
                .filter(intent -> intentService.getIntentState(intent.key()) != WITHDRAWN)
                .collect(Collectors.toSet());
        intentsToWithdrawn.stream()
                .map(Intent::key)
                .forEach(keysToWithdrawn::add);
        CompletableFuture<Void> completableFuture = new CompletableFuture<>();
        IntentListener listener = e -> {
            if (e.type() == IntentEvent.Type.WITHDRAWN) {
                keysToWithdrawn.remove(e.subject().key());
            }
            if (keysToWithdrawn.isEmpty()) {
                completableFuture.complete(null);
            }
        };
        intentService.addListener(listener);
        intentsToWithdrawn.forEach(intentService::withdraw);
        try {
            if (!intentsToWithdrawn.isEmpty()) {
                // Wait 1.5 seconds for each Intent
                completableFuture.get(intentsToWithdrawn.size() * 1500L, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            print("Encountered exception while withdrawing intents: " + e.toString());
        } finally {
            intentService.removeListener(listener);
        }
        intentsToWithdrawn.forEach(intentService::purge);
    }

    private void wipeOutFlows() {
        print("Wiping Flows");
        FlowRuleService flowRuleService = get(FlowRuleService.class);
        DeviceAdminService deviceAdminService = get(DeviceAdminService.class);
        for (Device device : deviceAdminService.getDevices()) {
            flowRuleService.purgeFlowRules(device.id());
        }
    }

    private void wipeOutGroups() {
        print("Wiping groups");
        GroupService groupService = get(GroupService.class);
        DeviceAdminService deviceAdminService = get(DeviceAdminService.class);
        for (Device device : deviceAdminService.getDevices()) {
            groupService.purgeGroupEntries(device.id());
        }
    }

 
    private void wipeOutHosts() {
        print("Wiping hosts");
        HostAdminService hostAdminService = get(HostAdminService.class);
        while (hostAdminService.getHostCount() > 0) {
            try {
                for (Host host : hostAdminService.getHosts()) {
                    hostAdminService.removeHost(host.id());
                }
            } catch (Exception e) {
                log.info("Unable to wipe-out hosts", e);
            }
        }
    }

    private void wipeOutDevices() {
        print("Wiping devices");
        DeviceAdminService deviceAdminService = get(DeviceAdminService.class);
        while (deviceAdminService.getDeviceCount() > 0) {
            try {
                for (Device device : deviceAdminService.getDevices()) {
                    deviceAdminService.removeDevice(device.id());
                }
            } catch (Exception e) {
                log.info("Unable to wipe-out devices", e);
            }
        }
    }

    private void wipeOutLinks() {
        print("Wiping links");
        LinkAdminService linkAdminService = get(LinkAdminService.class);
        while (linkAdminService.getLinkCount() > 0) {
            try {
                for (Link link : linkAdminService.getLinks()) {
                    linkAdminService.removeLinks(link.src());
                    linkAdminService.removeLinks(link.dst());
                }
            } catch (Exception e) {
                log.info("Unable to wipe-out links", e);
            }
        }
    }

    private void wipeOutPacketRequests() {
        print("Wiping packet requests");
        PacketService service = get(PacketService.class);
        for (PacketRequest r : service.getRequests()) {
            service.cancelPackets(r.selector(), r.priority(), r.appId(), r.deviceId());
        }
    }

  



}
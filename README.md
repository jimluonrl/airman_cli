# airman_cli
onos cli overlay for airman

Run inside the ONOS VM (tested with ONOS tutorial VM)

build with:
mvn clean install

add app to onos with:
onos-app localhost reinstall! target/airman-1.0.oar

cli command run:
airman clear_host_table
airman clear_flow_table
airman clear_switch_table


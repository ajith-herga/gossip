Disributed Membership System
============================

This system maintains membership information by use of Gossip Protocol. The joining process can introduce itself to any
member of the system already present in the group. The members can Leave/Fail from the group. All updates are visible to 
all the members within 3 seconds. The status of the Join/Leave/Fail can be queired from the Log querier. 
The querier can do a key--value based search on all the running systems.

Steps to run the program:
<br/>
1. cd to the folder in terminal and run "make" (this will compile the program)
<br/>
2. run "source .settings" (this will load the environment settings and needs to be done always before the next step)
<br/>
3. If this is the first node, execute "jg"
<br/>
   You will see a machine_id in the format (hostname___post___timestamp) in the first 5 lines on the terminal after the server is started. This machine id needs to be used when you run the next machine.
<br/>
   If this is not the first node, run "jg machine_id" using machine_id for some machine that has already joined.
<br/>
4. To make a machine leave the system voluntarily, use CTRL+C
<br/>
5. To make a machine fail, find the process id of the machine using ps -ef|grep GossipServer and then kill the process using kill -9.
<br/>
6. To query the distributed logs run "jc key:value" 
<br/>
   Use ".*" (including the double quotes) in key or value if you wish to query for only one of them.
<br/>
7. The logs are of the form: machine_id:(Joined/Left/Failed) at Date and Time.
<br/>

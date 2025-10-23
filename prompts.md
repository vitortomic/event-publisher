take a look at the xeep-api project. its a small spring boot application. then take a  
look at event-publisher in event-publisher directory. I want you to update             
event-publisher to have postgres connectivity using xeep-api as a template. instead of 
a user table it should have a table called event, which has columns event_id of type  
string and and and enum column called status with options being LIVE and NOT_LIVE.     
make sure in the ddl script to put in a constraint for status column to only have      
these two possible values. also create an event controller similiar to user controller 
with an endpoint to add events and a separate endpoint to update the status. make     
sure to follow the design choices of xeep-api, use hikari CP for database connections  
as well as jdbi and repository similar to user dao. make sure to also create the
flyway database migration for creating the event table.


update the event controller to use event dto implemented as a java record instead of
exposing the model itself


create for me a simple server using node js and express that has an endpoint :eventId/score which returns a json which looks like this
{ "eventId": "1234", "currentScore": "0:0"}  note that current score is a current score of for example a soccer match. this 
server need to simulate a soccer match so the score on each side of : shold increase
by one randomly after a time for each event id. basically when first called it will
be 0:0 , but then after a while it should randomly increase the score of some side


when the update event endpoint is called, if the status is set to live we need
to schedule a job that will query the node server score endpoint every 10 seconds.
create a score service that will call the node server every 10 seconds and for now just log the results.
use a spring thread pool task scheduler to schedule task which will do fetching and logging of the score.
scheduled task should be stored in a conncurent hash map where key is event id and value is the scheduled future
make sure to also cancel the job when an event is updated to status not live, and on application shutdown.

take a look at the xeep-api project. its a small spring boot application. then take a  │
│    look at event-publisher in event-publisher directory. I want you to update             │
│    event-publisher to have postgres connectivity using xeep-api as a template. instead of │
│     a user table it should have a table called event, which has columns event_id of type  │
│    string and and and enum column called status with options being LIVE and NOT_LIVE.     │
│    make sure in the ddl script to put in a constraint for status column to only have      │
│    these two possible values. also create an event controller similiar to user controller │
│     with an endpoint to add events and a separate endpoint to update the status. make     │
│    sure to follow the design choices of xeep-api, use hikari CP for database connections  │
│    as well as jdbi and repository similar to user dao. make sure to also create the       │
│    flyway database migration for creating the event table.


update the event controller to use event dto implemented as a java record instead of │
│     exposing the model itself

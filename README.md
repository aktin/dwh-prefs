DWH Configuration backend
=========================

Stores configuration parameters in relational database tables (SQL).

Other Java modules can use the interface with dependency injection.

Testing
-------

Test the database scripts with jdbc:hsqldb:mem:tempdb... 
e.g. similar to `http://stackoverflow.com/questions/11396219/init-database-for-test-purpose-during-maven-test-phase`


Configuration Parameters
------------------------

Configuration parameters are stored in a relational database table
in the AKTIN database.

Implements Java interface to read/write all values
Implements Restful interface which cannot read (WO) values, but write them


tls.keystore.path (R) keystore containing key and certificates for TLS
local.name (W) local name for this site/clinic, 
local.contact.name (W)
local.contact.email (W)

i2b2.project (R) i2b2 project id "Demo"
i2b2.crc.ds (R) i2b2 jndi datasource "java:/QueryToolDemoDS"
i2b2.lastimport (R) timestamp of last import

smtp.server (W)
smtp.port (W)
smtp.user (W)
smtp.password (WO)
smtp.auth (W) [plain|ssl|...]

query.notification.email (W) list of email addresses to receive notifications for queries
query.result.dir (R)
exchange.lastcontact (R) timestamp of last contact to broker via direct connection or received email timestamp
exchange.method (W) https|email
exchange.https.interval (W) interval in hours between polling connections to broker
exchange.https.broker (W) server name of the AKTIN broker
exchange.https.pool (W) server name of AKTIN pool
exchange.inbox.address (W) email address to receive queries
exchange.inbox.interval (W) interval in hours between checking for new emails
exchange.inbox.server (W) server configuration to check for query emails
exchange.inbox.port (W)
exchange.inbox.protocol (W) [imap|pop3]
exchange.inbox.user (W)
exchange.inbox.password (WO)


-- Multiple Brokers --
DWH can be registered with multiple brokers.
broker = {
	name: 'AKTIN Pilot',
	keystore: '',
	lastcontact,
	exchange_method,
	
	https : {
		interval (W) interval in hours between polling connections to broker
		broker (W) server name of the AKTIN broker
		pool (W) server name of AKTIN pool
	}
	// or
	
	inbox : {
		address (W) email address to receive queries
		interval (W) interval in hours between checking for new emails
		server (W) server configuration to check for query emails
		port (W)
		protocol (W) [imap|pop3]
		user (W)
		password (WO)
	}
}
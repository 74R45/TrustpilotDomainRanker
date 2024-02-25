# Trustpilot Domain Ranker

This project implements a service that periodically pulls recently reviewed stores
from Trustpilot.com and ranks them based on the number of recent reviews and monthly traffic
(as indicated on Vstat.info).

The project was implemented using [Scala](https://www.scala-lang.org/),
[Akka Streams](https://doc.akka.io/docs/akka/current/stream/index.html),
[Scala Scraper](https://index.scala-lang.org/ruippeixotog/scala-scraper),
and [Circe](https://index.scala-lang.org/circe/circe).

### _Usage_

1. Install [Docker Compose](https://docs.docker.com/compose/install/).
2. Run `docker compose up -d` in this directory.
3. Get relevant domains in `out/dashboard.txt`.

### _Some nuances_

- At the current stage the service pulls data from just the first pages in
categories "Electronics Store", "Jewelry Store", and "Clothing Store".
- Collected data is kept in memory, therefore once the service restarts
the dashboard will be reset.
- If Trustpilot.com or Vstat.info change their website layout, some parameters
in [Collector.scala](src/main/scala/com/x74r45/domain_ranker/Collector.scala)
may need to be modified.

### _What's next?_

- Scrape all pages in all categories;
- Restore service state after restart (i.e. Redis snapshotting);
- Take run parameters from a config file or environment variables;
- Add Unit testing.
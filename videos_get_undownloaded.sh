curl -X POST -H 'Content-type: application/json' \
  'http://netgear.rohidekar.com:7474/db/data/cypher' -d '
  {
     "query": "START n=node('$1') MATCH n-->c WHERE HAS(c.url) AND NOT HAS(c.downloaded_video) RETURN c.created,c.url,c.title order by c.created desc", "params":{}
  }'

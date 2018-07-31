# Scielo Harvester

`scielo-harvester` provides a fast and easy way to download articles from journals published at [scielo.org](http://scielo.org).

## Features
- [OAI-PMH](https://www.openarchives.org/pmh/) Client by using a [Camel](https://github.com/cbadenes/camel-oaipmh) Workflow
- HTTP Client by using a [Java HTML Parser](https://jsoup.org)
- Along with the content of the article, it provides the list of articles that cite it. (based on [CitedBy Restful API](http://docs.scielo.org/projects/citedby)
- JSON serialization in a .gz file:
    ```
	{
    	"id": "S2340-98942016000100002",
    	"doi": "",
    	"title": "Aspectos a considerar en una actualización de la normativa nacional en materia de legionelosis",
    	"language": "es",
    	"description": "Objetivo: En España, el vigente Real Decreto 865/2003 establece una serie de medidas ...",
    	"text": "Aspectos a considerar en una actualizacion de la normativa nacional ..",
    	"citedBy": ["S2340-989420160002000774","S2345-98942446000540052"],
    	"keywords": ["Legionella pneumophila", "legionelosis", "instalaciones de riesgo", "torres de refrigeración"],
    	"labels": ["Legionella pneumophila", "risk systems", "legionellosis", "cooling towers"],
    	"journal": {
    		"id": "2340-9894",
    		"name": "Ars Pharmaceutica (Internet)",
    		"site": "scielo.isciii.es"
    	}
    }
	```

## Quick Start 
1. Clone this repo and move into its top-level directory.

	```
	git clone https://github.com/cbadenes/scielo-harvester.git
	```
1. Run the service by: `harvest.sh`
1. You should be able to monitor the progress by console logs

- The above command downloads articles published in sites specified within `src/main/resources/sites.csv`.
- All articles are saved at: `corpus/articles.json.gz` 
- Once the download is complete, check the result by `gunzip -c corpus/articles.json.gz | head -1`


## Contact
This repository is maintained by [Carlos Badenes-Olmedo](mailto:cbadenes@gmail.com). Please send me an e-mail or open a GitHub issue if you have questions. 



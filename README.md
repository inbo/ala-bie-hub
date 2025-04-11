# ala-bie-hub
###    [![Build Status](https://app.travis-ci.com/AtlasOfLivingAustralia/ala-bie-hub.svg?branch=develop)](https://app.travis-ci.com/AtlasOfLivingAustralia/ala-bie-hub) [![E2E test status](https://github.com/AtlasOfLivingAustralia/ala-bie-hub/actions/workflows/e2e.yml/badge.svg)](https://github.com/AtlasOfLivingAustralia/ala-bie-hub/actions/workflows/e2e.yml)

This is the BIE (Biodiversity Information Explorer) front end - merge of old ala-bie and bie-plugin. 

THE BIE handles general search duties and serves taxon/species pages.
For more details on the architecture see [BIE index](http://github.com/AtlasOfLivingAustralia/bie-index)

### Languages

The bie-plugin uses ISO-639 language codes, particularly ISO-639-3, drawn from http://www.sil.org/iso639-3/ and the AIATSIS codes, drawn from https://aiatsis.gov.au/

### Blacklisted External Information

It is possible to blacklist sources of external information
that is either incorrect or not relevant.
Blacklisting is performed by pattern matching and can be configured by URLs that give a specific blacklist.
Blacklists are configured as a map of possible blacklists against the information in a document.
For example:

```yaml
external:
  blacklist: file:///data/ala-bie/config/blacklist.json
```

An example blacklist file can be found [here](src/test/resources/test-blacklist.json).
It contains metadata descibing the intent of the blacklist and
a list of entries that will cause the blacklist to trigger.

Each blacklist entry can trigger on some combination of:

* **source** The URL of the original source of the data.
* **name** The supplied name of taxon.
* **title** The title of the article

Currently, the blacklist is only used with the Encyclopedia of Life external source.


### Common Names Pull

It is possible to "pull" common names with special status into their own section and have them displayed in a special way.
To do this, use the following configuration settings:

* `vernacularName.pull.active` Set to true for a pull display (*false* by default)
* `vernacularName.pull.categories` A comma-separated list of status values that will cause the names to be pulled (*empty* by default)
* `vernacularName.pull.label` The label for the pull section in the names tab (*Special Common Names* by default)
* `vernacularName.pull.labelDetail` The detail to put into the label title (empty by default)
* `vernacularName.pull.showHeader` List these names in the header, just below the preferred common name (*false* by default)
* `vernacularName.pull.showLanguage` Show the language of the common name (*false* by default)

### Change log
See the [releases page](https://github.com/AtlasOfLivingAustralia/ala-bie-hub/releases) for this repository.

### Plugin tabs

(upstream PR?)
You can add your own tabs to species show page and configure the tabs' content.
#### How to configure plugin tab

To contribute a plugin tab:
- create species list for the tab
  - csv with 2 columns: scientificName and contentFilePath (currently only static html files are supported).
  - 2nd column name is configurable, see explanation below
  - contentFilePath is the relative file path of the html file; the file content will be loaded into tab 
  - upload the csv and create the species list
  - example: 
```csv
"scientificName","contentFilePath"
"Anser anser","abv/grauwe-gans.html"
"Branta canadensis","abv/canadese-gans.html"    
```
- the assets have to be built with the branding and placed in <tab-id> folder under dist folder (or wherever the static content is served from) 
- add the tab ID to show.pluginTabs config value (comma-separated list in case of > 1 tab)
- add config file (example plugin tabs file can be found [here](src/test/resources/test-plugin-tabs.json))
- set pluginTabsConfig config value to the config file url
- add i18n for the tab label in messages(_*).properties under key label.plugintab.<tab ID>

```yaml
show:
  tabs: overview,gallery,names,classification,records,literature,sequences,data-partners
  pluginTabs: abv

pluginTabsConfig: file:///config/bie-hub/plugin-tabs.json
```
```properties
label.plugintab.abv=ABV rapport
```

An example plugin tabs file can be found [here](src/test/resources/test-plugin-tabs.json).
```json
{
  "tabs": [
    {
      "tab": "abv",
      "speciesList": "dr28",
      "contentKey": "contentFilePath"
    }
  ]
}
```
tab: tab ID  (must be the same as in plugin-tabs.json)
speciesList:  druid of the species list
contentKey: 2nd column in the species list csv (it will be stored as kvp in the species list)
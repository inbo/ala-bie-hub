/*
 * Copyright (C) 2022 Atlas of Living Australia
 * All Rights Reserved.
 *
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 */

package au.org.ala.bie

import com.google.common.util.concurrent.RateLimiter
import grails.converters.JSON
import groovy.json.JsonSlurper
import org.jsoup.Jsoup

import java.nio.charset.StandardCharsets

/**
 * Controller that proxies external webservice calls to get around cross domain issues
 * and to make consumption of services easier from javascript.
 */
class ExternalSiteController {
    def externalSiteService
    def webService

    RateLimiter eolRateLimiter = RateLimiter.create(1.0) // rate max requests per second (Double)
    RateLimiter genbankRateLimiter = RateLimiter.create(3.0) // rate max requests per second (Double)

    def index() {}

    def wikipedia = {
        def results
        if (params.name) {
            results = externalSiteService.searchWikipedia(params.name)
        } else if (params.redirect) {
            results = externalSiteService.searchWikipedia(params.redirect)
        }
        render results
    }

    def genbank = {
        genbankRateLimiter.acquire()

        def searchStrings = params.list("s")
        def searchParams = searchStrings.collect { "(${it}[Organism])" }.join(" OR ")

        def genbankBase = grailsApplication.config.literature?.genbank?.url ?: "https://eutils.ncbi.nlm.nih.gov"

        def pageSize = 20
        def page = (params.int('page') ?: 1)
        def retStart = (page - 1) * pageSize

        def esearchUrl =
                "${genbankBase}/entrez/eutils/esearch.fcgi" +
                        "?db=nuccore" +
                        "&term=${URLEncoder.encode(searchParams, 'UTF-8')}" +
                        "&retmode=json" +
                        "&retstart=${retStart}" +
                        "&retmax=${pageSize}"

        log.debug "NCBI ESEARCH URL = ${esearchUrl}"

        def searchJson = new JsonSlurper().parseText(esearchUrl.toURL().text)

        def ids = searchJson?.esearchresult?.idlist ?: []
        def totalResults = (searchJson?.esearchresult?.count ?: "0") as int

        def formattedResults = []

        if (ids) {

            def esummaryUrl =
                    "${genbankBase}/entrez/eutils/esummary.fcgi" +
                            "?db=nuccore" +
                            "&retmode=json" +
                            "&id=${ids.join(',')}"

            def summaryJson = new JsonSlurper().parseText(esummaryUrl.toURL().text)

            ids.each { id ->
                def doc = summaryJson?.result?.get(id)
                if (!doc) return

                def link = "https://www.ncbi.nlm.nih.gov/nuccore/${doc.uid}"

                formattedResults << [
                        link                : link,
                        title               : doc.title,
                        description         : doc.organism,
                        furtherDescription  : "Accession: ${doc.accessionversion} | Length: ${doc.slen}"
                ]
            }
        }

        response.setContentType("application/json")
        render([
                total      : totalResults,
                resultsUrl : esearchUrl,
                results    : formattedResults
        ] as JSON)
    }

    def bhl() {
        def start = params.int('start', 0)
        def rows = params.int('rows', 10)
        def fulltext = params.boolean('fulltext', false)
        def searchStrings = params.list("s")
        def model = externalSiteService.searchBhl(searchStrings, start, rows, fulltext)
        withFormat {
            json {
                response.setContentType("application/json")
                render (model as JSON)
            }
            '*' {
                if (request.xhr) {
                    render(template: 'bhl', model: model)
                } else {
                    model
                }
            }
        }
    }

    def ausTraitsSummary() {
        def results = externalSiteService.getAusTraitsSummary(params)
        render results as JSON
    }

    def ausTraitsCount(){
        def results = externalSiteService.getAusTraitsCount(params)
        render results as JSON
    }

    def ecopedia() {
        def nodeType = params.nodeType
        def commonName = params.commonName
        def results = externalSiteService.searchEcopedia(nodeType, commonName)
        render(
                text: results,
                contentType: 'application/json',
                encoding: 'UTF-8'
        )
    }

    /**
     * Stream csv file from Austraits download endpoint directly to client
     * @return
     */
    def ausTraitsCSVDownload(){
        if(!params.s || params.s == "") {
            render(status: 400, text: "Bad Request. Missing mandatory params")
            return
        }
        def url = externalSiteService.generateAusTraitsDownloadUrl(params)
        def newUrl  = new URL(url)
        try{
            newUrl.withInputStream {it ->
            response.setHeader ("Content-disposition", "attachment;filename=${params.s}.csv")
            response.contentType = 'text/csv'
            response.outputStream << it
            response.outputStream.flush()}
        } catch (Exception e) {
            log.error("${e.message}")
            return render(status: 500, text: "Internal Server error")
        }
    }

    def scholar = {
        def searchStrings = params.list("s")
        def searchParams = "\"" + searchStrings.join("\" OR \"") + "\""
        def scholarBase = grailsApplication.config.literature?.scholar?.url ?: "https://scholar.google.com"
        def url = scholarBase + "/scholar?q=" + URLEncoder.encode(searchParams, "UTF-8")
        def doc = Jsoup.connect(url).userAgent("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6").referrer("http://www.google.com").timeout(10*1000).get()
        def totalResultsRaw = doc.select("div[id=gs_ab_md]").get(0).text()
        def matcher = totalResultsRaw =~ "About ([0-9\\,]{1,}) results \\([0-9\\.]{1,} sec\\)"
        def found = matcher.find()
        def totalResults = 0
        def formattedResults = []

        if(found){
            totalResults = matcher.group(1)
            def results = doc.select("div[class=gs_r]")
            results.each { result ->
                def link = result.select("a").attr("href")
                if(!link.startsWith("http")){
                    link =  scholarBase + link
                }
                def title = result.select("a").text()
                def descEl = result.select("div[class=gs_a]")
                def description = !descEl.empty ? descEl.get(0)?.text() : ""
                def furthEl = result.select("div[class=gs_rs]")
                def furtherDescription = !furthEl.empty ? furthEl.get(0)?.text() : ""
                formattedResults << [link:link,title:title,description:description, furtherDescription:furtherDescription]
            }
        }
        response.setContentType("application/json")
        render ([total:totalResults, resultsUrl:url, results:formattedResults] as JSON)
    }

    /**
     * Proxy autocomplete requests to bie-index
     *
     */
    def proxyAutocomplete = {
        URL url = ( "${grailsApplication.config.getProperty("bie.index.url")}/search/auto" + params.toQueryString() ).toURL()
        StringBuilder content = new StringBuilder()
        BufferedReader bufferedReader

        try {
            HttpURLConnection connection = url.openConnection()
            connection.setRequestMethod("GET")
            connection.connect()
            bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream()))
            String line
            // read from the connection via the bufferedreader
            while ((line = bufferedReader.readLine()) != null) {
                content.append(line + "\n")
            }
            response.setContentType(connection.getContentType())
            response.status = connection.getResponseCode()
            render content.toString() //render url.getText()
        } catch (Exception e) {
            // will bubble up to Grails and trigger an error page
            log.error "${e.message}", e
        } finally {
            if (bufferedReader) {
                bufferedReader.close() // can throw exception but passing on to Grails error handling
            }
        }
    }

    def setImages() {
        def result = webService.get(grailsApplication.config.bie.index.url +
                '/api/setImages?prefer=' + URLEncoder.encode(params.prefer, StandardCharsets.UTF_8) +
                "&name=" + URLEncoder.encode(params.name, StandardCharsets.UTF_8) +
                "&guid=" + URLEncoder.encode(params.guid, StandardCharsets.UTF_8) +
                "&hide=" + URLEncoder.encode(params.hide, StandardCharsets.UTF_8))
        render status: result.statusCode
    }

    def setUrl() {
        def url = grailsApplication.config.bie.index.url +
                '/api/setUrl?url=' + URLEncoder.encode(params.url, StandardCharsets.UTF_8) +
                "&name=" + URLEncoder.encode(params.name, StandardCharsets.UTF_8) +
                "&guid=" + URLEncoder.encode(params.guid, StandardCharsets.UTF_8)
        def result = webService.get(url)
        render status: result.statusCode
    }
}

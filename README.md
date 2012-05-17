NYC Find a Fountain
=============

Author: Joel Kemp, @mrjoelkemp

Created: Summer 2011

Updated: Periodically

## Purpose
Find the NYC public drinking fountains near you!
On the Android Market: https://market.android.com/details?id=com.findafountain

This application was created with the Android SDK, and the Java programming language. It served as my first mobile development project and was a lot of fun to build! The source has been provided to serve as a learning reference for beginning Android developers.

## Data Source
The drinking fountain locations were provided by the NYC Department of Parks and Recreation via the NYC Datamine. The data was originally provided in an unusable GIS map format which had to be converted to KML and ultimately to CSV. I then wrote a script to parse the CSV into geo-coordinates and push that data into my server's MySQL database. 

## Implementation
The Android client asynchronously fetches the JSON listing of all fountains from my custom, PHP, RESTful web server with a MySQL backend. The client parses the JSON entries and locally stores the data on the client phone in an SQLite database. The phone then uses the Google Maps API in conjunction with my lazy-loading (i.e., loading the fountains within the user's current area and padded, viewing rectangle) algorithm to show the fountains close to the user's location.

## Updates
The application uses the NYC Opendata platform (as the datasource) which exposes the set of fountains in a JSON format. The Jackson Parser was utilized to do progressive parsing of the large dataset.
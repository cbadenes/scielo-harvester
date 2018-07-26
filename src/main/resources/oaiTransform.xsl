<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:dc="http://purl.org/dc/elements/1.1/"
                version="1.0">
    <xsl:output method="text" encoding="ISO-8859-1" />
    <xsl:variable name="newline" select="'&#xA;'"/>
    <xsl:template match="dc">
        <xsl:value-of select="dc:identifier" />
        <xsl:text>;;</xsl:text>
        <xsl:value-of select="dc:title" />
        <xsl:text>;;</xsl:text>
        <xsl:value-of select="dc:description" />
        <xsl:value-of select="$newline" />
    </xsl:template>
</xsl:stylesheet>
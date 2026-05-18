package com.txmusic.player;

public class Song {
    public long id;
    public String uri, title, artist, album;
    public long duration, size, albumId;
    public Song(long id, String uri, String title, String artist, String album, long duration, long size){
        this(id, uri, title, artist, album, duration, size, -1);
    }
    public Song(long id, String uri, String title, String artist, String album, long duration, long size, long albumId){
        this.id=id; this.uri=uri; this.title=title; this.artist=artist; this.album=album; this.duration=duration; this.size=size; this.albumId=albumId;
    }
}

/*
 * Created on 03.05.2015
 * Author: Veselin Markov.
 */
package com.kylentt.musicplayer.common.media.audio.meta_tag.audio.dsf

import com.kylentt.musicplayer.common.media.audio.meta_tag.audio.exceptions.CannotReadException
import com.kylentt.musicplayer.common.media.audio.meta_tag.audio.generic.AudioFileReader2
import com.kylentt.musicplayer.common.media.audio.meta_tag.audio.generic.GenericAudioHeader
import com.kylentt.musicplayer.common.media.audio.meta_tag.audio.generic.Utils
import com.kylentt.musicplayer.common.media.audio.meta_tag.audio.iff.IffHeaderChunk
import com.kylentt.musicplayer.common.media.audio.meta_tag.tag.Tag
import com.kylentt.musicplayer.common.media.audio.meta_tag.tag.TagException
import com.kylentt.musicplayer.common.media.audio.meta_tag.tag.id3.AbstractID3v2Tag
import com.kylentt.musicplayer.common.media.audio.meta_tag.tag.id3.ID3v22Tag
import com.kylentt.musicplayer.common.media.audio.meta_tag.tag.id3.ID3v23Tag
import com.kylentt.musicplayer.common.media.audio.meta_tag.tag.id3.ID3v24Tag
import com.kylentt.musicplayer.core.sdk.VersionHelper
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.util.logging.Level

/**
 * Reads the ID3 Tags as specified by [](http://dsd-guide.com/sites/default/files/white-papers/DSFFileFormatSpec_E.pdf) DSFFileFormatSpec_E.pdf .
 *
 * @author Veselin Markov (veselin_m84 a_t yahoo.com)
 */
class DsfFileReader : AudioFileReader2() {
	@Throws(CannotReadException::class, IOException::class, UnsupportedOperationException::class)
	override fun getEncodingInfo(file: Path): GenericAudioHeader {
		if (!VersionHelper.hasOreo()) throw UnsupportedOperationException("Require API >= 26")

		FileChannel.open(file).use { fc ->
			val dsd: DsdChunk? =
				DsdChunk.readChunk(Utils.readFileDataIntoBufferLE(fc, DsdChunk.DSD_HEADER_LENGTH))
			return if (dsd != null) {
				val fmtChunkBuffer =
					Utils.readFileDataIntoBufferLE(
						fc,
						IffHeaderChunk.SIGNATURE_LENGTH + DsdChunk.CHUNKSIZE_LENGTH
					)
				FmtChunk.readChunkHeader(fmtChunkBuffer)
					?.readChunkData(dsd, fc)
					?: throw CannotReadException("$file Not a valid dsf file. Content does not include 'fmt ' chunk")
			} else {
				throw CannotReadException("$file Not a valid dsf file. Content does not start with 'DSD '")
			}
		}
	}

	@Throws(CannotReadException::class, IOException::class, UnsupportedOperationException::class)
	override fun getTag(path: Path): Tag {
		if (!VersionHelper.hasOreo()) throw UnsupportedOperationException("Require API >= 26")

		FileChannel.open(path).use { fc ->
			val dsd: DsdChunk? =
				DsdChunk.readChunk(Utils.readFileDataIntoBufferLE(fc, DsdChunk.DSD_HEADER_LENGTH))
			return if (dsd != null) {
				logger.config(
					path.toString() + ":actualFileSize:" + fc.size() + ":" + dsd.toString()
				)
				readTag(fc, dsd, path.toString())!!
			} else {
				throw CannotReadException("$path Not a valid dsf file. Content does not start with 'DSD '.")
			}
		}
	}

	/**
	 * Reads the ID3v2 tag starting at the `tagOffset` position in the
	 * supplied file.
	 *
	 * @param fc the filechannel from which to read
	 * @param dsd  the dsd chunk
	 * @param fileName
	 * @return the read tag or an empty tag if something went wrong. Never
	 * `null`.
	 * @throws IOException if cannot read file.
	 */
	@Throws(CannotReadException::class, IOException::class)
	private fun readTag(fc: FileChannel, dsd: DsdChunk, fileName: String): Tag? {
		return if (dsd.metadataOffset > 0) {
			fc.position(dsd.metadataOffset)
			if (fc.size() - fc.position() >= DsfChunkType.ID3.code.length) {
				val id3Chunk: ID3Chunk? =
					ID3Chunk.readChunk(
						Utils.readFileDataIntoBufferLE(
							fc,
							(fc.size() - fc.position()).toInt()
						)
					)
				if (id3Chunk != null) {
					val version =
						id3Chunk.dataBuffer[AbstractID3v2Tag.FIELD_TAG_MAJOR_VERSION_POS].toInt()
					try {
						when (version) {
							ID3v22Tag.majorVersion.toInt() -> ID3v22Tag(id3Chunk.dataBuffer, fileName)
							ID3v23Tag.MAJOR_VERSION.toInt() -> ID3v23Tag(id3Chunk.dataBuffer, fileName)
							ID3v24Tag.MAJOR_VERSION.toInt() -> ID3v24Tag(id3Chunk.dataBuffer, fileName)
							else -> {
								logger.log(
									Level.WARNING,
									"$fileName Unknown ID3v2 version $version. Returning an empty ID3v2 Tag."
								)
								null
							}
						}
					} catch (e: TagException) {
						throw CannotReadException("$fileName Could not read ID3v2 tag:corruption")
					}
				} else {
					logger.log(Level.WARNING, "$fileName No existing ID3 tag(1)")
					null
				}
			} else {
				logger.log(Level.WARNING, "$fileName No existing ID3 tag(2)")
				null
			}
		} else {
			logger.log(Level.WARNING, "$fileName No existing ID3 tag(3)")
			null
		}
	}
}
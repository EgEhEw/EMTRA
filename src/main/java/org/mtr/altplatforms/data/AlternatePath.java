package org.mtr.altplatforms.data;

import org.mtr.core.data.Data;
import org.mtr.core.data.PathData;
import org.mtr.core.data.TransportMode;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.serializer.SerializedDataBase;
import org.mtr.core.serializer.WriterBase;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class AlternatePath implements SerializedDataBase {

	public final int stopIndex;
	public final long primaryPlatformId;
	public final long alternatePlatformId;
	public final ObjectArrayList<PathData> alternatePath = new ObjectArrayList<>();

	public AlternatePath(int stopIndex, long primaryPlatformId, long alternatePlatformId, ObjectArrayList<PathData> alternatePath) {
		this.stopIndex = stopIndex;
		this.primaryPlatformId = primaryPlatformId;
		this.alternatePlatformId = alternatePlatformId;
		this.alternatePath.addAll(alternatePath);
	}

	public AlternatePath(ReaderBase readerBase) {
		stopIndex = (int) readerBase.getLong("stopIndex", 0);
		primaryPlatformId = readerBase.getLong("primaryPlatformId", 0);
		alternatePlatformId = readerBase.getLong("alternatePlatformId", 0);
		readerBase.iterateReaderArray("alternatePath", alternatePath::clear, readerBaseChild -> alternatePath.add(new PathData(readerBaseChild)));
	}

	@Override
	public void updateData(ReaderBase readerBase) {
	}

	@Override
	public void serializeData(WriterBase writerBase) {
		writerBase.writeLong("stopIndex", stopIndex);
		writerBase.writeLong("primaryPlatformId", primaryPlatformId);
		writerBase.writeLong("alternatePlatformId", alternatePlatformId);
		writerBase.writeDataset(alternatePath, "alternatePath");
	}

	public void writePathCache(Data data, TransportMode transportMode) {
		PathData.writePathCache(alternatePath, data, transportMode);
	}
}

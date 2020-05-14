package wooteco.subway.admin.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.WeightedMultigraph;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import wooteco.subway.admin.domain.Line;
import wooteco.subway.admin.domain.LineStation;
import wooteco.subway.admin.domain.Station;
import wooteco.subway.admin.dto.response.PathResponse;
import wooteco.subway.admin.dto.response.StationResponse;
import wooteco.subway.admin.repository.LineRepository;
import wooteco.subway.admin.repository.StationRepository;

@Service
public class PathService {

    private final LineRepository lineRepository;
    private final StationRepository stationRepository;

    public PathService(LineRepository lineRepository,
        StationRepository stationRepository) {
        this.lineRepository = lineRepository;
        this.stationRepository = stationRepository;
    }

    @Transactional
    public PathResponse findPath(Long sourceId, Long targetId) {
        List<Line> lines = lineRepository.findAll();
        Map<Long, Station> stations = stationRepository.findAll()
            .stream()
            .collect(Collectors.toMap(Station::getId, station -> station));
        WeightedMultigraph<Long, LineStationEdge> graph = new WeightedMultigraph<>(
            LineStationEdge.class);
        for (Station station : stations.values()) {
            graph.addVertex(station.getId());
        }
        for (Line line : lines) {
            Set<LineStation> lineStations = line.getStations();
            for (LineStation lineStation : lineStations) {
                if (Objects.isNull(lineStation.getPreStationId())) {
                    continue;
                }
                LineStationEdge lineStationEdge = LineStationEdge.of(lineStation);
                graph.addEdge(lineStation.getPreStationId(), lineStation.getStationId(),
                    lineStationEdge);
                graph.setEdgeWeight(lineStationEdge, lineStationEdge.getDistance());
            }
        }
        DijkstraShortestPath<Long, LineStationEdge> dijkstraShortestPath = new DijkstraShortestPath<>(graph);
        GraphPath<Long, LineStationEdge> path = dijkstraShortestPath.getPath(sourceId, targetId);
        List<Long> stationIds = path.getVertexList();
        List<StationResponse> responses = stationIds.stream()
            .map(stations::get)
            .map(StationResponse::of)
            .collect(Collectors.toList());
        List<LineStationEdge> edges = path.getEdgeList();
        int distance = edges.stream()
            .mapToInt(LineStationEdge::getDistance)
            .sum();
        int duration = edges.stream()
            .mapToInt(LineStationEdge::getDuration)
            .sum();
        return new PathResponse(responses, duration, distance);
    }

    static class LineStationEdge extends DefaultWeightedEdge {
        private int distance;
        private int duration;

        private LineStationEdge(int distance, int duration) {
            this.distance = distance;
            this.duration = duration;
        }

        public static LineStationEdge of(LineStation lineStation) {
            return new LineStationEdge(lineStation.getDistance(), lineStation.getDuration());
        }

        public int getDistance() {
            return distance;
        }

        public int getDuration() {
            return duration;
        }
    }
}
from tools.rtklib_solution_summary import summarize_pos_lines


def test_summarize_pos_lines_counts_quality_and_satellites():
    quality, satellites = summarize_pos_lines(
        [
            "% header",
            "2026/06/21 17:09:48.200 49.0 16.0 342.0 4 3 1.0",
            "2026/06/21 17:09:48.400 49.0 16.0 342.0 2 12 1.0",
            "2026/06/21 17:09:48.600 49.0 16.0 342.0 1 15 1.0",
        ],
    )

    assert quality["4"] == 1
    assert quality["2"] == 1
    assert quality["1"] == 1
    assert satellites["3"] == 1
    assert satellites["12"] == 1
    assert satellites["15"] == 1



const size = 1000;
var array = new Array(size);


array.fill(Math.random());
const rand = () => String(Math.floor(Math.random() * size));


for (var i = 0; i < array.length; i++) {
    array[i] = rand();
}


function partition(arr, start, end) {
    const pivotVal = arr[end];
    let pivotIndex = start;
    for (let i = start; i < end; i++) {
        if (arr[i] < pivotVal) {
            [arr[i], arr[pivotIndex]] = [arr[pivotIndex], arr[i]];
            pivotIndex++;
        }
    }
    [arr[pivotIndex], arr[end]] = [arr[end], arr[pivotIndex]];
    return pivotIndex;
};

function quickSort(arr, start, end) {
    if (start >= end) {
        return;
    }
    let index = partition(arr, start, end);
    quickSort(arr, start, index - 1);
    quickSort(arr, index + 1, end);
}


var start = new Date();
quickSort(array, 0, array.length - 1);
var stop = new Date();
var diff = stop - start;
console.log(">>>>>>>>>>>> quicksort end: time = ",diff," ms");






